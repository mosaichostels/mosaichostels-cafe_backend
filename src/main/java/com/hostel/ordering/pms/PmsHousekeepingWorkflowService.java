package com.hostel.ordering.pms;

import static com.hostel.ordering.pms.PmsJson.elements;
import static com.hostel.ordering.pms.PmsJson.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostel.ordering.model.CleaningAssignment;
import com.hostel.ordering.model.CleaningAssignment.Status;
import com.hostel.ordering.pms.dto.BookingSummary;
import com.hostel.ordering.pms.dto.CleaningTask;
import com.hostel.ordering.pms.dto.CleaningTaskList;
import com.hostel.ordering.repository.CleaningAssignmentRepository;
import com.hostel.ordering.repository.UserRepository;
import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.IdempotencyService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cleaning to-do list (eZee departures, one task per date and room) plus Mongo-backed assignments.
 * Mongo holds ids, room-name snapshots, dates, status, assignee and notes only; never booking or guest data.
 */
@Service
public class PmsHousekeepingWorkflowService {

    static final int MAX_DAYS_AWAY = 31;
    private static final Pattern ROOM_ID = Pattern.compile("[0-9]{1,20}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern OBJECT_ID = Pattern.compile("[0-9a-f]{24}");
    private static final int MAX_USERNAME = 100;

    public record AssignRequest(String assignee, String notes) {}

    public record UpdateRequest(String status, String notes, String assignee, Long version) {}

    private final PmsFrontDeskService departures;
    private final PmsHousekeepingFeed feed;
    private final CleaningAssignmentRepository repo;
    private final UserRepository users;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final Clock clock;

    @Autowired
    public PmsHousekeepingWorkflowService(PmsFrontDeskService departures, PmsHousekeepingFeed feed,
            CleaningAssignmentRepository repo, UserRepository users, IdempotencyService idempotency,
            AuditService audit) {
        this(departures, feed, repo, users, idempotency, audit, Clock.systemUTC());
    }

    PmsHousekeepingWorkflowService(PmsFrontDeskService departures, PmsHousekeepingFeed feed,
            CleaningAssignmentRepository repo, UserRepository users, IdempotencyService idempotency,
            AuditService audit, Clock clock) {
        this.departures = departures;
        this.feed = feed;
        this.repo = repo;
        this.users = users;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- read ----

    public CleaningTaskList tasks(String dateText) {
        LocalDate date = dateText == null || dateText.isBlank() ? PmsDates.today(clock) : parseDate(dateText);
        checkWindow(date);
        Cached<List<BookingSummary>> deps = departures.departures(date, date);
        Cached<JsonNode> hk = feed.inhouseStatus();

        Map<String, JsonNode> roomsByName = new HashMap<>();
        for (JsonNode r : elements(hk.value().path("roomlist"))) {
            if (!text(r, "roomid").isEmpty()) {
                roomsByName.putIfAbsent(nameKey(text(r, "roomname")), r); // first wins; O and 0 stay distinct
            }
        }
        Map<String, Integer> turnovers = new LinkedHashMap<>(); // roomId -> departures
        Map<String, Integer> unmatched = new LinkedHashMap<>(); // trimmed name -> departures
        Map<String, JsonNode> roomById = new HashMap<>();
        for (BookingSummary b : deps.value()) {
            if (PmsDates.leadingDate(b.departure()).filter(d -> !d.equals(date)).isPresent()) {
                continue;
            }
            String name = b.room() == null ? "" : b.room().strip();
            JsonNode room = roomsByName.get(nameKey(name));
            if (room == null) {
                unmatched.merge(name, 1, Integer::sum);
            } else {
                String id = text(room, "roomid");
                roomById.put(id, room);
                turnovers.merge(id, 1, Integer::sum);
            }
        }
        Map<String, CleaningAssignment> stored = new HashMap<>();
        for (CleaningAssignment a : repo.findByDepartureDate(date.toString())) {
            stored.put(a.getRoomId(), a);
        }
        List<CleaningTask> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : turnovers.entrySet()) {
            JsonNode r = roomById.get(e.getKey());
            items.add(task(date, e.getKey(), text(r, "unitid"), text(r, "roomname"), text(r, "roomtypename"),
                    e.getValue(), stored.remove(e.getKey())));
        }
        for (CleaningAssignment a : stored.values()) { // assigned work stays visible if the departure vanished
            items.add(task(date, a.getRoomId(), a.getUnitId(), a.getRoomName(), a.getRoomType(), 0, a));
        }
        items.sort(Comparator.comparing(CleaningTask::roomName, PmsHousekeepingService::natural)
                .thenComparing(CleaningTask::roomId));
        List<CleaningTaskList.Unmatched> um = new ArrayList<>();
        unmatched.forEach((n, c) -> um.add(new CleaningTaskList.Unmatched(n, c)));
        return new CleaningTaskList(items, um, Math.min(deps.fetchedAtMs(), hk.fetchedAtMs()),
                deps.stale() || hk.stale());
    }

    private static CleaningTask task(LocalDate date, String roomId, String unitId, String name, String type,
            int turnovers, CleaningAssignment a) {
        return new CleaningTask(taskKey(date, roomId), roomId, unitId, name, type, date.toString(), turnovers,
                a == null ? null : a.getAssigneeUsername(), a == null ? Status.UNASSIGNED.name() : a.getStatus().name(),
                a == null ? null : a.getNotes(), a == null ? null : a.getId(), a == null ? null : a.getVersion());
    }

    // ---- write ----

    public CleaningAssignment assign(String taskKey, AssignRequest req, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String canonical = canonicalTaskKey(taskKey);
        String assignee = requireAssignee(req.assignee());
        String notes = checkNotes(req.notes());
        return once(key, "assign|" + canonical + "|" + assignee + "|" + notes, () -> {
            String[] parts = canonical.split("\\|");
            CleaningAssignment a = repo.findByTaskKey(canonical).orElse(null);
            boolean created = a == null;
            if (created) {
                a = newAssignment(canonical, parts[0], parts[1]);
            }
            a.setAssigneeUsername(assignee);
            if (a.getStatus() == Status.UNASSIGNED) {
                a.setStatus(Status.ASSIGNED);
            }
            if (notes != null) {
                a.setNotes(notes);
            }
            CleaningAssignment saved = save(a);
            audit.logAction("CLEANING_ASSIGN", "assignmentId=" + saved.getId() + " taskKey=" + canonical
                    + " assignee=" + assignee);
            return saved;
        });
    }

    /** Any valid status transition is allowed (DONE is not final); a non-UNASSIGNED status needs an assignee. */
    public CleaningAssignment update(String id, UpdateRequest req, String idempotencyKey) {
        String key = requireKey(idempotencyKey);
        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (id == null || !OBJECT_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid assignment id");
        }
        Status status = parseStatus(req.status());
        String assignee = req.assignee() == null ? null : requireAssignee(req.assignee());
        String notes = checkNotes(req.notes());
        if (status == null && assignee == null && notes == null) {
            throw new IllegalArgumentException("Nothing to update");
        }
        return once(key, "update|" + id + "|" + status + "|" + assignee + "|" + notes + "|" + req.version(), () -> {
            CleaningAssignment a = repo.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
            if (req.version() != null && !req.version().equals(a.getVersion())) {
                throw conflict();
            }
            if (assignee != null) {
                a.setAssigneeUsername(assignee);
                if (status == null && a.getStatus() == Status.UNASSIGNED) {
                    a.setStatus(Status.ASSIGNED);
                }
            }
            if (status != null) {
                a.setStatus(status);
                if (status == Status.UNASSIGNED) {
                    a.setAssigneeUsername(null);
                }
            }
            if (a.getStatus() != Status.UNASSIGNED && a.getAssigneeUsername() == null) {
                throw new IllegalArgumentException("An assignee is required for this status");
            }
            if (notes != null) {
                a.setNotes(notes);
            }
            CleaningAssignment saved = save(a);
            audit.logAction("CLEANING_UPDATE", "assignmentId=" + saved.getId() + " status=" + saved.getStatus()
                    + " assignee=" + saved.getAssigneeUsername());
            return saved;
        });
    }

    // shortcut: check-then-put is not atomic; two identical in-flight requests can both write (the unique
    // taskKey index and @Version still keep the data consistent). Upgrade to an atomic claim if that matters.
    private CleaningAssignment once(String key, String fingerprint, Supplier<CleaningAssignment> op) {
        String fpKey = "cleaning-fp:" + key;
        String resKey = "cleaning-res:" + key;
        String seen = idempotency.getIfPresent(fpKey, String.class);
        if (seen != null) {
            if (!seen.equals(fingerprint)) {
                throw new PmsIdempotencyMismatchException("Idempotency-Key reused for a different request");
            }
            CleaningAssignment first = idempotency.getIfPresent(resKey, CleaningAssignment.class);
            if (first != null) {
                return first;
            }
        }
        CleaningAssignment result = op.get();
        idempotency.put(resKey, result);
        idempotency.put(fpKey, fingerprint);
        return result;
    }

    private CleaningAssignment newAssignment(String taskKey, String date, String roomId) {
        JsonNode room = null;
        for (JsonNode r : elements(feed.inhouseStatus().value().path("roomlist"))) {
            if (roomId.equals(text(r, "roomid"))) {
                room = r;
                break;
            }
        }
        if (room == null) {
            throw new IllegalArgumentException("Unknown room");
        }
        CleaningAssignment a = new CleaningAssignment();
        a.setTaskKey(taskKey);
        a.setRoomId(roomId);
        a.setUnitId(text(room, "unitid"));
        a.setRoomName(text(room, "roomname"));
        a.setRoomType(text(room, "roomtypename"));
        a.setDepartureDate(date);
        a.setCreatedAt(clock.millis());
        return a;
    }

    private CleaningAssignment save(CleaningAssignment a) {
        a.setUpdatedAt(clock.millis());
        try {
            return repo.save(a);
        } catch (OptimisticLockingFailureException | DuplicateKeyException e) {
            throw conflict();
        }
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "This task was changed by someone else. Reload and retry.");
    }

    // ---- validation ----

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Idempotency-Key is invalid");
        }
        return key;
    }

    private String requireAssignee(String username) {
        String u = username == null ? "" : username.strip();
        if (u.isEmpty() || u.length() > MAX_USERNAME) {
            throw new IllegalArgumentException("A valid assignee is required");
        }
        if (users.findByUsername(u).isEmpty()) {
            throw new IllegalArgumentException("Assignee not found");
        }
        return u;
    }

    private static String checkNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String n = notes.strip();
        if (n.length() > CleaningAssignment.MAX_NOTES) {
            throw new IllegalArgumentException("Notes must be at most 300 characters");
        }
        return n;
    }

    private static Status parseStatus(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Status.valueOf(s.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status");
        }
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates must be in yyyy-MM-dd format");
        }
    }

    private void checkWindow(LocalDate date) {
        long away = Math.abs(date.toEpochDay() - PmsDates.today(clock).toEpochDay());
        if (away > MAX_DAYS_AWAY) {
            throw new IllegalArgumentException("The date must be within 31 days of today");
        }
    }

    /** Rebuilds the key from a parsed date and a digits-only room id; nothing raw is forwarded. */
    private String canonicalTaskKey(String raw) {
        String[] p = raw == null ? new String[0] : raw.split("\\|", -1);
        if (p.length != 2 || !ROOM_ID.matcher(p[1]).matches()) {
            throw new IllegalArgumentException("Invalid task key");
        }
        LocalDate d = parseDate(p[0]);
        checkWindow(d);
        return taskKey(d, p[1]);
    }

    private static String taskKey(LocalDate date, String roomId) {
        return date + "|" + roomId;
    }

    private static String nameKey(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}

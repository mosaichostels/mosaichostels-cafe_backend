package com.hostel.ordering.pms;

import static com.hostel.ordering.pms.PmsJson.elements;
import static com.hostel.ordering.pms.PmsJson.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostel.ordering.pms.dto.CleaningTask;
import com.hostel.ordering.pms.dto.CleaningTaskList;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Read-only cleaning to-do: one task per departure date and room, built from the eZee departures feed and the
 * room list. Nothing is stored; no booking or guest data leaves this class.
 */
@Service
public class PmsHousekeepingWorkflowService {

    static final int MAX_DAYS_AWAY = 31;

    /** The only two departure fields the to-do needs: the room name and the leaving date. */
    private record Departure(String room, String end) {}

    private final EzeeGateway gateway;
    private final PmsCache cache;
    private final PmsProperties props;
    private final PmsHousekeepingFeed feed;
    private final Clock clock;

    @Autowired
    public PmsHousekeepingWorkflowService(EzeeGateway gateway, PmsCache cache, PmsProperties props,
            PmsHousekeepingFeed feed) {
        this(gateway, cache, props, feed, Clock.systemUTC());
    }

    PmsHousekeepingWorkflowService(EzeeGateway gateway, PmsCache cache, PmsProperties props,
            PmsHousekeepingFeed feed, Clock clock) {
        this.gateway = gateway;
        this.cache = cache;
        this.props = props;
        this.feed = feed;
        this.clock = clock;
    }

    // ---- read ----

    public CleaningTaskList tasks(String dateText) {
        LocalDate date = dateText == null || dateText.isBlank() ? PmsDates.today(clock) : parseDate(dateText);
        checkWindow(date);
        Cached<List<Departure>> deps = departures(date);
        Cached<JsonNode> hk = feed.inhouseStatus();

        Map<String, JsonNode> roomsByName = new HashMap<>();
        Map<String, Integer> nameCount = new HashMap<>();
        for (JsonNode r : elements(hk.value().path("roomlist"))) {
            String k = nameKey(text(r, "roomname"));
            if (!text(r, "roomid").isEmpty() && !k.isEmpty()) { // blank names never match; O and 0 stay distinct
                roomsByName.putIfAbsent(k, r);
                nameCount.merge(k, 1, Integer::sum);
            }
        }
        Map<String, Integer> turnovers = new LinkedHashMap<>(); // roomId -> departures
        Map<String, Integer> unmatched = new LinkedHashMap<>(); // trimmed name -> departures
        Set<String> ambiguous = new HashSet<>();
        Map<String, JsonNode> roomById = new HashMap<>();
        for (Departure b : deps.value()) {
            if (PmsDates.leadingDate(b.end()).filter(d -> !d.equals(date)).isPresent()) {
                continue;
            }
            String name = b.room();
            String k = nameKey(name);
            JsonNode room = k.isEmpty() ? null : roomsByName.get(k);
            if (room == null || nameCount.get(k) > 1) { // a shared name cannot be pinned to one bed
                unmatched.merge(name, 1, Integer::sum);
                if (room != null) {
                    ambiguous.add(name);
                }
            } else {
                String id = text(room, "roomid");
                roomById.put(id, room);
                turnovers.merge(id, 1, Integer::sum);
            }
        }
        List<CleaningTask> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : turnovers.entrySet()) {
            JsonNode r = roomById.get(e.getKey());
            items.add(new CleaningTask(taskKey(date, e.getKey()), e.getKey(), text(r, "unitid"), text(r, "roomname"),
                    text(r, "roomtypename"), date.toString(), e.getValue()));
        }
        items.sort(Comparator.comparing(CleaningTask::roomName, PmsHousekeepingService::natural)
                .thenComparing(CleaningTask::roomId));
        List<CleaningTaskList.Unmatched> um = new ArrayList<>();
        unmatched.forEach((n, c) -> um.add(new CleaningTaskList.Unmatched(n, c, ambiguous.contains(n))));
        return new CleaningTaskList(items, um, Math.min(deps.fetchedAtMs(), hk.fetchedAtMs()),
                deps.stale() || hk.stale());
    }

    private Cached<List<Departure>> departures(LocalDate date) {
        String key = "departures:" + date + ":" + date; // dates only: keys are logged
        return PmsReads.load(cache, key, props.ttl("departures", Duration.ofSeconds(60)), () -> {
            JsonNode reply = gateway.json(EzeeFamily.CONNECTIVITY, "DepartureList",
                    Map.of("Date", Map.of("from_date", date.toString(), "to_date", date.toString())),
                    PmsPriority.HUB);
            List<Departure> out = new ArrayList<>();
            for (JsonNode r : elements(reply.path("Reservations").path("Reservation"))) {
                for (JsonNode t : elements(r.path("BookingTran"))) { // one row per room, as eZee lists them
                    out.add(new Departure(text(t, "RoomName"), text(t, "End")));
                }
            }
            return out;
        });
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

    private static String taskKey(LocalDate date, String roomId) {
        return date + "|" + roomId;
    }

    private static String nameKey(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}

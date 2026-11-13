package com.hostel.ordering.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.hostel.ordering.model.ScheduleDay;
import com.hostel.ordering.model.ScheduleTick;
import com.hostel.ordering.pms.PmsDates;
import com.hostel.ordering.repository.ScheduleDayRepository;
import com.hostel.ordering.repository.ScheduleTickRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Housekeeping schedule storage: which days the admin submitted and which task slots were ticked.
 * The task list itself lives in the app. Atomic first-wins for ticks is the unique index.
 */
@Service
public class ScheduleService {

    /** Carry-over looks back this many days; leftovers older than that drop off the list. */
    static final int LOOKBACK_DAYS = 31;
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,60}");
    private static final Set<String> SLOTS = Set.of("ONCE", "AM", "PM");

    public record TickResult(boolean alreadyTicked, String doneBy) {}

    public record DaySummary(String date, boolean submitted, Long submittedAt, List<ScheduleTick> ticks) {}

    private final ScheduleDayRepository days;
    private final ScheduleTickRepository ticks;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Autowired
    public ScheduleService(ScheduleDayRepository days, ScheduleTickRepository ticks, MongoTemplate mongoTemplate) {
        this(days, ticks, mongoTemplate, Clock.system(PmsDates.IST));
    }

    public ScheduleService(ScheduleDayRepository days, ScheduleTickRepository ticks, MongoTemplate mongoTemplate,
            Clock clock) {
        this.days = days;
        this.ticks = ticks;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public boolean isSubmitted(String date) {
        return days.existsByDate(parse(date).toString());
    }

    /** True only when this call newly submitted today. */
    public boolean markSubmitted(String date, String username) {
        String d = requireToday(date);
        if (days.existsByDate(d)) {
            return false;
        }
        ScheduleDay day = new ScheduleDay();
        day.setDate(d);
        day.setSubmittedBy(username);
        day.setSubmittedAt(clock.millis());
        try {
            days.save(day);
            return true;
        } catch (DuplicateKeyException race) {
            return false; // another submit won the unique index
        }
    }

    public TickResult tick(String date, String taskKey, String slot, String username) {
        validateKeyAndSlot(taskKey, slot);
        LocalDate day = parse(date);
        LocalDate today = today();
        if (day.isAfter(today) || day.isBefore(today.minusDays(LOOKBACK_DAYS))) {
            throw new IllegalArgumentException("Date must be within the last " + LOOKBACK_DAYS + " days, not in the future");
        }
        String d = day.toString();
        if (!days.existsByDate(d)) {
            throw new IllegalArgumentException("That day has not been submitted");
        }
        var existing = ticks.findByDateAndTaskKeyAndSlot(d, taskKey, slot);
        if (existing.isPresent()) {
            return new TickResult(true, existing.get().getDoneBy());
        }
        ScheduleTick t = new ScheduleTick();
        t.setDate(d);
        t.setTaskKey(taskKey);
        t.setSlot(slot);
        t.setDoneBy(username);
        t.setDoneAt(clock.millis());
        try {
            ticks.save(t);
            return new TickResult(false, username);
        } catch (DuplicateKeyException race) {
            String first = ticks.findByDateAndTaskKeyAndSlot(d, taskKey, slot).map(ScheduleTick::getDoneBy).orElse(null);
            return new TickResult(true, first);
        }
    }

    /** Today only. False when the tick does not exist; repeating a confirm keeps the first confirmer. */
    public boolean confirm(String date, String taskKey, String slot, String username) {
        String d = requireToday(date);
        validateKeyAndSlot(taskKey, slot);
        if (ticks.findByDateAndTaskKeyAndSlot(d, taskKey, slot).isEmpty()) {
            return false;
        }
        mongoTemplate.updateFirst(
                query(where("date").is(d).and("taskKey").is(taskKey).and("slot").is(slot).and("confirmedBy").is(null)),
                new Update().set("confirmedBy", username).set("confirmedAt", clock.millis()),
                ScheduleTick.class);
        return true;
    }

    /** One entry per submitted day in the range (at most 32 days inclusive). */
    public List<DaySummary> days(String from, String to) {
        LocalDate f = parse(from);
        LocalDate t = parse(to);
        if (f.isAfter(t) || t.toEpochDay() - f.toEpochDay() > LOOKBACK_DAYS) {
            throw new IllegalArgumentException("The date range must be at most " + (LOOKBACK_DAYS + 1) + " days");
        }
        Map<String, List<ScheduleTick>> byDate = ticks.findByDateBetween(f.toString(), t.toString()).stream()
                .collect(Collectors.groupingBy(ScheduleTick::getDate));
        return days.findByDateBetweenOrderByDate(f.toString(), t.toString()).stream()
                .map(d -> new DaySummary(d.getDate(), true, d.getSubmittedAt(),
                        byDate.getOrDefault(d.getDate(), List.of())))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Returns the normalised date; IllegalArgumentException unless it is today (IST). */
    public String requireToday(String date) {
        LocalDate d = parse(date);
        if (!d.equals(today())) {
            throw new IllegalArgumentException("Only today's date is allowed");
        }
        return d.toString();
    }

    private static LocalDate parse(String s) {
        try {
            return LocalDate.parse(s == null ? "" : s.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates must be in yyyy-MM-dd format");
        }
    }

    private static void validateKeyAndSlot(String taskKey, String slot) {
        if (taskKey == null || !KEY.matcher(taskKey).matches()) {
            throw new IllegalArgumentException("Invalid task key");
        }
        if (slot == null || !SLOTS.contains(slot)) {
            throw new IllegalArgumentException("Slot must be ONCE, AM or PM");
        }
    }
}

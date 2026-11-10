package com.hostel.ordering.pms;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The property's calendar day (Asia/Kolkata) and the date range a hub read accepts. */
public final class PmsDates {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final int MAX_RANGE_DAYS = 30;
    private static final Pattern LEADING_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");

    public record Range(LocalDate from, LocalDate to) {}

    private PmsDates() {}

    public static LocalDate today() {
        return today(Clock.systemUTC());
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(IST));
    }

    /** Blank bounds default to today. Fixed messages: they reach the caller as a 400. */
    public static Range range(String from, String to, Clock clock) {
        LocalDate today = today(clock);
        LocalDate f = parse(from, today);
        LocalDate t = parse(to, today);
        if (f.isAfter(t)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        if (t.toEpochDay() - f.toEpochDay() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("The date range must not exceed 30 days");
        }
        return new Range(f, t);
    }

    /** The leading yyyy-MM-dd of eZee text such as "2026-11-10 14:00:00"; empty if absent or invalid. */
    public static Optional<LocalDate> leadingDate(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = LEADING_DATE.matcher(text.strip());
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(m.group(1)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static LocalDate parse(String s, LocalDate fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates must be in yyyy-MM-dd format");
        }
    }
}

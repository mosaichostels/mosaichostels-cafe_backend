package com.hostel.ordering.pms;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostel.ordering.pms.dto.BookingSummary;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Helpers shared by the hub services for reading eZee reservation replies. */
final class PmsJson {

    private PmsJson() {}

    /** eZee sends a lone child as an object in some replies; treat it as a one-element list. */
    static List<JsonNode> elements(JsonNode n) {
        if (n.isArray()) {
            List<JsonNode> l = new ArrayList<>();
            n.forEach(l::add);
            return l;
        }
        return n.isObject() ? List.of(n) : List.of();
    }

    static String text(JsonNode n, String field) {
        return n.path(field).asText("").strip();
    }

    static int nights(String start, String end) {
        var s = PmsDates.leadingDate(start);
        var e = PmsDates.leadingDate(end);
        if (s.isEmpty() || e.isEmpty()) {
            return 0;
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(s.get(), e.get()));
    }

    /** Summary row for one BookingTran of reservation r (guest name and source live on the reservation). */
    static BookingSummary summary(JsonNode r, JsonNode t) {
        String guest = (text(r, "FirstName") + " " + text(r, "LastName")).strip();
        String start = text(t, "Start");
        String end = text(t, "End");
        return new BookingSummary(text(t, "SubBookingId"), guest, text(t, "RoomName"), text(t, "RoomTypeName"),
                start, end, nights(start, end), text(t, "CurrentStatus"), text(r, "Source"));
    }
}

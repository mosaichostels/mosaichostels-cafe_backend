package com.hostel.ordering.pms;

import com.fasterxml.jackson.databind.JsonNode;
import static com.hostel.ordering.pms.PmsJson.elements;
import static com.hostel.ordering.pms.PmsJson.nights;
import static com.hostel.ordering.pms.PmsJson.text;

import com.hostel.ordering.pms.dto.BookingSummary;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Arrivals, departures and in-house lists from eZee, cached. Never logs values. */
@Service
public class PmsFrontDeskService {

    private final EzeeGateway gateway;
    private final PmsCache cache;
    private final PmsProperties props;
    private final PmsHousekeepingFeed feed;

    public PmsFrontDeskService(EzeeGateway gateway, PmsCache cache, PmsProperties props, PmsHousekeepingFeed feed) {
        this.gateway = gateway;
        this.cache = cache;
        this.props = props;
        this.feed = feed;
    }

    public Cached<List<BookingSummary>> arrivals(LocalDate from, LocalDate to) {
        return dated("arrivals", "ArrivalList", from, to);
    }

    public Cached<List<BookingSummary>> departures(LocalDate from, LocalDate to) {
        return dated("departures", "DepartureList", from, to);
    }

    public Cached<List<BookingSummary>> inHouse() {
        Cached<JsonNode> c = feed.inhouseStatus();
        List<BookingSummary> items = new ArrayList<>();
        for (JsonNode g : elements(c.value().path("checkinguestlist"))) {
            if (!PmsHousekeepingFeed.isInHouse(g)) {
                continue;
            }
            String in = PmsDates.leadingDate(text(g, "checkindate")).map(LocalDate::toString).orElse("");
            String out = PmsDates.leadingDate(text(g, "checkoutdate")).map(LocalDate::toString).orElse("");
            items.add(new BookingSummary(text(g, "reservationno"), text(g, "guestname"), text(g, "room"),
                    text(g, "roomtype"), in, out, nights(in, out), text(g, "bookingstatus"),
                    text(g, "businesssource")));
        }
        return new Cached<>(items, c.fetchedAtMs(), c.stale());
    }

    private Cached<List<BookingSummary>> dated(String name, String requestType, LocalDate from, LocalDate to) {
        String key = name + ":" + from + ":" + to; // dates only: keys are logged
        return PmsReads.load(cache, key, props.ttl("arrivals", Duration.ofSeconds(60)), () -> {
            JsonNode reply = gateway.json(EzeeFamily.CONNECTIVITY, requestType,
                    Map.of("Date", Map.of("from_date", from.toString(), "to_date", to.toString())),
                    PmsPriority.HUB);
            return mapReservations(reply);
        });
    }

    /** One row per BookingTran: a reservation holding several rooms yields several rows sharing the guest. */
    private static List<BookingSummary> mapReservations(JsonNode reply) {
        List<BookingSummary> items = new ArrayList<>();
        for (JsonNode r : elements(reply.path("Reservations").path("Reservation"))) {
            for (JsonNode t : elements(r.path("BookingTran"))) {
                items.add(PmsJson.summary(r, t));
            }
        }
        return items;
    }
}

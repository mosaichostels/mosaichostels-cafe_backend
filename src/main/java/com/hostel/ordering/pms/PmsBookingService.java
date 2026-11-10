package com.hostel.ordering.pms;

import static com.hostel.ordering.pms.PmsJson.elements;
import static com.hostel.ordering.pms.PmsJson.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostel.ordering.pms.dto.BookingDetail;
import com.hostel.ordering.pms.dto.FolioSummary;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * One booking plus its folios from eZee, cached by id. Never logs values; keys hold only the id.
 * A cached detail (including phone and email) stays in the in-memory PmsCache past its TTL and is served
 * stale while eZee is down: accepted P0 stale-while-error design, bounded by MAX_KEYS, never persisted or logged.
 */
@Service
public class PmsBookingService {

    private final EzeeGateway gateway;
    private final PmsCache cache;
    private final PmsProperties props;

    public PmsBookingService(EzeeGateway gateway, PmsCache cache, PmsProperties props) {
        this.gateway = gateway;
        this.cache = cache;
        this.props = props;
    }

    public Cached<BookingDetail> detail(String rawId) {
        String id = PmsIds.bookingId(rawId);
        Duration ttl = props.ttl("booking", Duration.ofSeconds(30));
        Cached<BookingDetail> booking = PmsReads.load(cache, "booking:" + id, ttl, () -> {
            BookingDetail d = mapBooking(gateway.json(EzeeFamily.CONNECTIVITY, "FetchSingleBooking",
                    Map.of("BookingId", id), PmsPriority.HUB), id);
            if (d == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
            }
            return d;
        });
        Cached<List<FolioSummary>> folios;
        try {
            // a failing load throws, so the failure is never cached; the detail still renders without folios
            folios = PmsReads.load(cache, "folios:" + id, ttl, () -> mapFolios(gateway.json(EzeeFamily.KIOSK,
                    "RetrieveListofBills", Map.of("Authentication", Map.of("BookingId", id)), PmsPriority.HUB)));
        } catch (RuntimeException e) {
            folios = new Cached<>(List.of(), booking.fetchedAtMs(), true); // stale marks 'unavailable', not cached
        }
        BookingDetail b = booking.value();
        return new Cached<>(new BookingDetail(b.summary(), folios.value(), b.phone(), b.email()),
                Math.min(booking.fetchedAtMs(), folios.fetchedAtMs()), booking.stale() || folios.stale());
    }

    /** Null when eZee has no such booking (empty reply). */
    private static BookingDetail mapBooking(JsonNode reply, String id) {
        JsonNode r = first(reply.path("Reservations").path("Reservation"), null);
        if (r == null) {
            return null;
        }
        JsonNode t = first(r.path("BookingTran"), id);
        if (t == null) {
            return null;
        }
        var s = PmsJson.summary(r, t);
        String phone = firstNonBlank(text(t, "Mobile"), text(t, "Phone"), text(r, "Mobile"), text(r, "Phone"));
        String email = firstNonBlank(text(t, "Email"), text(r, "Email"));
        return new BookingDetail(s, List.of(), phone, email);
    }

    private static List<FolioSummary> mapFolios(JsonNode reply) {
        List<FolioSummary> out = new ArrayList<>();
        for (JsonNode f : elements(reply.path("Success").path("FolioList"))) {
            out.add(new FolioSummary(text(f, "foliono"), text(f, "CurrencyCode"), money(f, "TotalCharges"),
                    money(f, "PaidAmount"), money(f, "DueAmount")));
        }
        return out;
    }

    private static BigDecimal money(JsonNode n, String field) {
        try {
            String v = text(n, field).replace(",", "");
            return v.isEmpty() ? BigDecimal.ZERO : new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null; // present but unparsable: unknown, not zero
        }
    }

    /** The element whose SubBookingId equals wantedId, else the first; null if none. */
    private static JsonNode first(JsonNode n, String wantedId) {
        List<JsonNode> l = elements(n);
        if (l.isEmpty()) {
            return null;
        }
        if (wantedId != null) {
            for (JsonNode e : l) {
                if (wantedId.equals(text(e, "SubBookingId"))) {
                    return e;
                }
            }
        }
        return l.get(0);
    }

    private static String firstNonBlank(String... v) {
        for (String s : v) {
            if (!s.isEmpty()) {
                return s;
            }
        }
        return "";
    }

}

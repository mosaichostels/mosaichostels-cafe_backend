package com.hostel.ordering.pms;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostel.ordering.pms.dto.GuestHit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Guest search over the cached eZee guest list. Never logs names or the query; cache keys carry no query text. */
@Service
public class PmsGuestService {

    static final int MIN_QUERY = 2;
    static final int MAX_QUERY = 60;
    static final int MAX_HITS = 20;
    private static final Set<String> VIP = Set.of("yes", "1", "true", "vip");
    private static final String NAME_COLUMN = "guest name";
    private static final String STAYS_COLUMN = "total number of stays";

    /** Only what search needs; phone, email and address are dropped before caching. */
    record Guest(String id, String name, String lower, boolean vip) {}

    private final EzeeGateway gateway;
    private final PmsCache cache;
    private final PmsProperties props;

    public PmsGuestService(EzeeGateway gateway, PmsCache cache, PmsProperties props) {
        this.gateway = gateway;
        this.cache = cache;
        this.props = props;
    }

    public Cached<List<GuestHit>> search(String q) {
        String needle = q == null ? "" : q.strip();
        if (needle.length() < MIN_QUERY) {
            throw new IllegalArgumentException("The search text must be at least 2 characters");
        }
        if (needle.length() > MAX_QUERY) {
            throw new IllegalArgumentException("The search text must not exceed 60 characters");
        }
        String lower = needle.toLowerCase(Locale.ROOT);
        Cached<List<Guest>> all = PmsReads.load(cache, "guests:list", props.ttl("guests", Duration.ofMinutes(10)),
                () -> mapGuests(gateway.json(EzeeFamily.CONNECTIVITY, "GuestList", Map.of(), PmsPriority.HUB)));
        List<Guest> found = new ArrayList<>();
        for (Guest g : all.value()) {
            if (g.lower().contains(lower)) {
                found.add(g);
            }
        }
        found.sort(Comparator.comparing((Guest g) -> g.lower()).thenComparing(Guest::id));
        List<Guest> top = found.size() > MAX_HITS ? found.subList(0, MAX_HITS) : found;
        Map<String, Integer> stays = top.isEmpty() ? Map.of() : stays();
        List<GuestHit> hits = new ArrayList<>();
        for (Guest g : top) {
            hits.add(new GuestHit(g.id(), g.name(), g.vip(), stays.getOrDefault(g.lower(), 0)));
        }
        return new Cached<>(hits, all.fetchedAtMs(), all.stale());
    }

    private static List<Guest> mapGuests(JsonNode reply) {
        List<Guest> out = new ArrayList<>();
        for (JsonNode g : PmsJson.elements(reply.path("Guests"))) {
            String name = PmsJson.text(g, "Contact_person");
            if (!name.isEmpty()) {
                out.add(new Guest(PmsJson.text(g, "Id"), name, name.toLowerCase(Locale.ROOT),
                        VIP.contains(PmsJson.text(g, "VIPStatus").toLowerCase(Locale.ROOT))));
            }
        }
        return out;
    }

    /** Name to stay count. A failed call is not cached and degrades to no counts rather than failing the search. */
    private Map<String, Integer> stays() {
        try {
            return PmsReads.load(cache, "guests:stays", props.ttl("gueststays", Duration.ofMinutes(30)),
                    () -> countStays(gateway.text(EzeeFamily.GUEST_DB, "GuestDatabase", Map.of(), PmsPriority.HUB)))
                    .value();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /**
     * Sums the "Total Number of stays" column per trimmed lower-cased "Guest Name" (live-verified header, one row per
     * guest profile; some names repeat across profiles). A missing column or a non-numeric cell counts nothing.
     */
    static Map<String, Integer> countStays(String csv) {
        List<List<String>> rows = PmsCsv.parse(csv);
        int start = PmsCsv.firstNonBlank(rows);
        if (start == rows.size()) {
            return Map.of();
        }
        List<String> header = rows.get(start);
        int name = -1;
        int stays = -1;
        for (int i = 0; i < header.size(); i++) {
            String h = PmsCsv.headerName(header.get(i));
            if (name < 0 && NAME_COLUMN.equals(h)) {
                name = i;
            } else if (stays < 0 && STAYS_COLUMN.equals(h)) {
                stays = i;
            }
        }
        if (name < 0 || stays < 0) {
            return Map.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (int r = start + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (name < row.size() && stays < row.size() && !row.get(name).isBlank()) {
                try {
                    int n = Integer.parseInt(row.get(stays).strip());
                    if (n > 0) {
                        counts.merge(row.get(name).strip().toLowerCase(Locale.ROOT), n, Integer::sum);
                    }
                } catch (NumberFormatException ignored) {
                    // unreadable cell: no count rather than a guess
                }
            }
        }
        return counts;
    }
}

package com.hostel.ordering.pms;

import static com.hostel.ordering.pms.PmsJson.elements;
import static com.hostel.ordering.pms.PmsJson.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostel.ordering.pms.dto.RoomStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Room board built from the shared cached InhouseRoomStatus feed. Never logs values. */
@Service
public class PmsHousekeepingService {

    private static final Pattern DIGITS = Pattern.compile("[0-9]+|[^0-9]+");

    private final PmsHousekeepingFeed feed;

    public PmsHousekeepingService(PmsHousekeepingFeed feed) {
        this.feed = feed;
    }

    public Cached<List<RoomStatus>> rooms() {
        Cached<JsonNode> c = feed.inhouseStatus();
        JsonNode reply = c.value();
        Map<String, String> guestByRoom = new HashMap<>();
        for (JsonNode g : elements(reply.path("checkinguestlist"))) {
            // first guest in list order wins (dorm rooms hold several)
            guestByRoom.putIfAbsent(key(text(g, "room")), text(g, "guestname"));
        }
        List<RoomStatus> items = new ArrayList<>();
        for (JsonNode r : elements(reply.path("roomlist"))) {
            String id = text(r, "roomid");
            if (id.isEmpty()) {
                continue;
            }
            String name = text(r, "roomname");
            String guest = name.isEmpty() ? null : guestByRoom.get(key(name));
            items.add(new RoomStatus(id, text(r, "unitid"), text(r, "roomtypeid"), name, text(r, "roomtypename"),
                    r.path("hkstatus").asText(""), "yes".equalsIgnoreCase(text(r, "isblocked")), guest != null,
                    guest == null ? "" : guest));
        }
        items.sort(Comparator.comparing(RoomStatus::name, PmsHousekeepingService::natural)
                .thenComparing(RoomStatus::roomId));
        return new Cached<>(items, c.fetchedAtMs(), c.stale());
    }

    private static String key(String room) {
        return room.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Total order: tokens of ASCII digit runs and other runs compared pairwise (digits before text; digits by
     * numeric value without parsing; text case-insensitively), fewer tokens first, then raw compareTo.
     */
    static int natural(String a, String b) {
        List<String> x = tokens(a);
        List<String> y = tokens(b);
        for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
            int d = compareToken(x.get(i), y.get(i));
            if (d != 0) {
                return d;
            }
        }
        int d = Integer.compare(x.size(), y.size());
        return d != 0 ? d : a.compareTo(b);
    }

    private static List<String> tokens(String s) {
        List<String> t = new ArrayList<>();
        Matcher m = DIGITS.matcher(s);
        while (m.find()) {
            t.add(m.group());
        }
        return t;
    }

    private static boolean isDigits(String t) {
        char c = t.charAt(0);
        return c >= '0' && c <= '9';
    }

    private static int compareToken(String p, String q) {
        boolean pd = isDigits(p);
        boolean qd = isDigits(q);
        if (pd != qd) {
            return pd ? -1 : 1;
        }
        if (!pd) {
            return p.toLowerCase(Locale.ROOT).compareTo(q.toLowerCase(Locale.ROOT));
        }
        String ps = p.replaceFirst("^0+(?=.)", "");
        String qs = q.replaceFirst("^0+(?=.)", "");
        return ps.length() != qs.length() ? Integer.compare(ps.length(), qs.length()) : ps.compareTo(qs);
    }
}

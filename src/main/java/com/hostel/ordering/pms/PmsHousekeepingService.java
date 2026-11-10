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

    private static final Pattern DIGITS = Pattern.compile("\\d+|\\D+");

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
            String guest = guestByRoom.get(key(name));
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

    /** Digit runs compare by value, other runs case-insensitively. */
    static int natural(String a, String b) {
        Matcher x = DIGITS.matcher(a);
        Matcher y = DIGITS.matcher(b);
        while (x.find() && y.find()) {
            String p = x.group();
            String q = y.group();
            int d;
            if (Character.isDigit(p.charAt(0)) && Character.isDigit(q.charAt(0))) {
                String ps = p.replaceFirst("^0+(?=.)", "");
                String qs = q.replaceFirst("^0+(?=.)", "");
                d = ps.length() != qs.length() ? Integer.compare(ps.length(), qs.length()) : ps.compareTo(qs);
            } else {
                d = p.compareToIgnoreCase(q);
            }
            if (d != 0) {
                return d;
            }
        }
        return Integer.compare(a.length(), b.length()) == 0 ? a.compareTo(b) : Integer.compare(a.length(), b.length());
    }
}

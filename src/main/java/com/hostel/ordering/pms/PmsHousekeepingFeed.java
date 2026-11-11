package com.hostel.ordering.pms;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** One cached eZee InhouseRoomStatus call ({roomlist, checkinguestlist}) shared by the room board and the cleaning to-do. */
@Component
public class PmsHousekeepingFeed {

    static final String CACHE_KEY = "hk:inhouse";

    private final EzeeGateway gateway;
    private final PmsCache cache;
    private final PmsProperties props;

    public PmsHousekeepingFeed(EzeeGateway gateway, PmsCache cache, PmsProperties props) {
        this.gateway = gateway;
        this.cache = cache;
        this.props = props;
    }

    /** The returned JsonNode is shared across callers and must be treated read-only. */
    public Cached<JsonNode> inhouseStatus() {
        return PmsReads.load(cache, CACHE_KEY, props.ttl("hk", Duration.ofSeconds(30)),
                () -> gateway.json(EzeeFamily.HOUSEKEEPING, "InhouseRoomStatus", Map.of(), PmsPriority.HUB));
    }

    /** A write's pre-check and read-back may wait this long for a slot: 3 hub calls in ~1 s exceed 2 per 5 s. */
    static final Duration WRITE_WAIT = Duration.ofSeconds(7);

    /**
     * Reads eZee directly: never the cache and never another caller's in-flight load, so the value cannot be stale.
     * The cache entry is dropped before and after so later P1 reads reload instead of serving the pre-write board.
     */
    public JsonNode freshInhouseStatus() {
        cache.evict(CACHE_KEY);
        try {
            return gateway.json(EzeeFamily.HOUSEKEEPING, "InhouseRoomStatus", Map.of(), PmsPriority.HUB, WRITE_WAIT);
        } finally {
            cache.evict(CACHE_KEY);
        }
    }

    /** Live InhouseRoomStatus also lists guests already "Checked Out" and not-yet-arrived "Confirmed Reservation"s. */
    static boolean isInHouse(JsonNode guest) {
        String status = guest.path("bookingstatus").asText("").strip();
        return !status.equalsIgnoreCase("Checked Out") && !status.equalsIgnoreCase("Confirmed Reservation");
    }
}

package com.hostel.ordering.pms;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** One cached eZee InhouseRoomStatus call ({roomlist, checkinguestlist}) shared by the in-house list and the room board. */
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

    /** Drops the cached reply and reads eZee again; a write's pre-check and read-back must not see stale data. */
    public Cached<JsonNode> freshInhouseStatus() {
        cache.evict(CACHE_KEY);
        return inhouseStatus();
    }

    /** Live InhouseRoomStatus also lists guests already "Checked Out" and not-yet-arrived "Confirmed Reservation"s. */
    static boolean isInHouse(JsonNode guest) {
        String status = guest.path("bookingstatus").asText("").strip();
        return !status.equalsIgnoreCase("Checked Out") && !status.equalsIgnoreCase("Confirmed Reservation");
    }
}

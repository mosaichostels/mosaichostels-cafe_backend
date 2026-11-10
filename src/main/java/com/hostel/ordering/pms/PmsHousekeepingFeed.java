package com.hostel.ordering.pms;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** One cached eZee InhouseRoomStatus call ({roomlist, checkinguestlist}) shared by the in-house list and the room board. */
@Component
public class PmsHousekeepingFeed {

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
        return PmsReads.load(cache, "hk:inhouse", props.ttl("hk", Duration.ofSeconds(30)),
                () -> gateway.json(EzeeFamily.HOUSEKEEPING, "InhouseRoomStatus", Map.of(), PmsPriority.HUB));
    }
}

package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.pms.dto.ChargeCheck;
import com.hostel.ordering.pms.dto.RoomStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** The JSON names the Android client reads; the app has no custom Jackson config, so Spring's defaults apply. */
class PmsJsonNamesTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private List<String> names(Object o) {
        List<String> out = new ArrayList<>();
        mapper.valueToTree(o).fieldNames().forEachRemaining(out::add);
        return out;
    }

    @Test
    void hubDtoFieldNames() {
        BigDecimal one = BigDecimal.ONE;
        assertEquals(List.of("orderId", "bookingName", "room", "amount", "ourStatus", "eZeeStatus"),
                names(new ChargeCheck("o", "n", "r", one, "QUEUED", "FOUND")));
        assertEquals(List.of("roomId", "unitId", "roomTypeId", "name", "roomType", "hkStatus", "blocked", "occupied",
                "guestName"), names(new RoomStatus("1", "u", "t", "n", "rt", "Dirty", false, true, "g")));
    }

    @Test
    void reconcileEnvelopeFieldNames() {
        JsonNode n = mapper.valueToTree(new PmsReconcileService.Result(List.of(), 5L, false, true));
        List<String> out = new ArrayList<>();
        n.fieldNames().forEachRemaining(out::add);
        assertEquals(List.of("items", "asOf", "stale", "truncated"), out);
    }
}

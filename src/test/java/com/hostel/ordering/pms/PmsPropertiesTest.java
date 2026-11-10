package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PmsPropertiesTest {

    private static PmsProperties bind(Map<String, Object> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("pms", PmsProperties.class)
                .orElseGet(PmsProperties::new);
    }

    @Test
    void bindsConfiguredValues() {
        PmsProperties p = bind(Map.of(
                "pms.hub-per5s", "5",
                "pms.cafe-quiet-ms", "1500",
                "pms.writes.room-status", "true",
                "pms.canary.room-status[0]", "R101",
                "pms.ttl.arrivals", "20"));

        assertEquals(5, p.getHubPer5s());
        assertEquals(1500L, p.getCafeQuietMs());
        assertEquals(true, p.getWrites().get("room-status"));
        assertEquals(List.of("R101"), p.getCanary().get("room-status"));
        assertEquals(Duration.ofSeconds(20), p.ttl("arrivals", Duration.ofSeconds(45)));
    }

    @Test
    void unsetTtlFallsBack() {
        assertEquals(Duration.ofSeconds(45), bind(Map.of()).ttl("arrivals", Duration.ofSeconds(45)));
    }

    @Test
    void defaults() {
        PmsProperties p = bind(Map.of());

        assertTrue(p.isEnabled());
        assertEquals(2, p.getHubPer5s());
        assertEquals(3000L, p.getCafeQuietMs());
        assertEquals(2, p.getCafeReserve());
        assertTrue(p.getWrites().isEmpty());
        assertTrue(p.getCanary().isEmpty());
    }
}

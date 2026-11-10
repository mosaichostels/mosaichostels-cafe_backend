package com.hostel.ordering.pms;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for the rate-limited, cached, flag-gated eZee PMS gateway. */
@Component
@ConfigurationProperties("pms")
public class PmsProperties {

    private boolean enabled = true;
    private int hubPer5s = 3;
    private int cafeReserve = 2;
    /** Per-feature write flags; a missing feature means writes are off. */
    private Map<String, Boolean> writes = new HashMap<>();
    /** Per-feature target ids a write may touch while it is being proven. */
    private Map<String, List<String>> canary = new HashMap<>();
    /** Cache TTLs in seconds, keyed by cache name. */
    private Map<String, Long> ttl = new HashMap<>();

    public Duration ttl(String name, Duration fallback) {
        Long seconds = ttl.get(name);
        return seconds == null ? fallback : Duration.ofSeconds(seconds);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getHubPer5s() { return hubPer5s; }
    public void setHubPer5s(int hubPer5s) { this.hubPer5s = hubPer5s; }
    public int getCafeReserve() { return cafeReserve; }
    public void setCafeReserve(int cafeReserve) { this.cafeReserve = cafeReserve; }
    public Map<String, Boolean> getWrites() { return writes; }
    public void setWrites(Map<String, Boolean> writes) { this.writes = writes; }
    public Map<String, List<String>> getCanary() { return canary; }
    public void setCanary(Map<String, List<String>> canary) { this.canary = canary; }
    public Map<String, Long> getTtl() { return ttl; }
    public void setTtl(Map<String, Long> ttl) { this.ttl = ttl; }
}

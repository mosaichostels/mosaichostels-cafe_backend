package com.hostel.ordering.pms;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SuperAdmin view of the PMS gateway's health. It reports; it never fails because eZee is down. */
@RestController
@RequestMapping("/pms/status")
public class PmsStatusController {

    /** Flags and the probe verdict only: no credentials, canary ids or eZee text. */
    public record Status(boolean enabled, String eZee, long checkedAt, boolean stale, int limiterUsed,
            Map<String, Boolean> writeFlags) {}

    private static final String KEY = "status";

    private final PmsProperties props;
    private final PmsFlags flags;
    private final PmsCache cache;
    private final EzeeGateway gateway;
    private final PmsRateLimiter limiter;

    public PmsStatusController(PmsProperties props, PmsFlags flags, PmsCache cache, EzeeGateway gateway,
            PmsRateLimiter limiter) {
        this.props = props;
        this.flags = flags;
        this.cache = cache;
        this.gateway = gateway;
        this.limiter = limiter;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public Status status() {
        Map<String, Boolean> writeFlags = new LinkedHashMap<>();
        for (String feature : flags.configuredFeatures()) {
            writeFlags.put(feature, flags.isWriteEnabled(feature));
        }
        long now = System.currentTimeMillis();
        if (!props.isEnabled()) {
            return new Status(false, "DISABLED", now, false, limiter.usedInWindow(), writeFlags);
        }
        String verdict = "OK";
        long checkedAt = now;
        boolean stale = false;
        try {
            Cached<Boolean> probe = cache.getOrLoad(KEY, props.ttl("status", Duration.ofSeconds(60)), () -> {
                gateway.json(EzeeFamily.KIOSK, "RetrieveCurrency", Map.of(), PmsPriority.HUB);
                return Boolean.TRUE;
            });
            checkedAt = probe.fetchedAtMs();
            stale = probe.stale();
            // stale means the latest refresh failed and only the last good value was served
            if (stale) {
                verdict = "UPSTREAM";
            }
        } catch (PmsAuthException e) {
            verdict = "AUTH";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            verdict = "UPSTREAM";
        } catch (Exception e) {
            verdict = "UPSTREAM";
        }
        return new Status(true, verdict, checkedAt, stale, limiter.usedInWindow(), writeFlags);
    }
}

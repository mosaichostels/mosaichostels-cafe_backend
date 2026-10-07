package com.hostel.ordering.security;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-username brake on failed logins. The per-IP limit alone cannot do this job: the whole
 * hostel shares one IP, so it has to be generous, which leaves one account open to guessing.
 * ponytail: in-memory per instance and an attacker can lock a name for a minute; upgrade to a
 * persisted counter if that ever matters.
 */
@Component
public class LoginThrottle {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED = 10_000;

    private static final class Entry {
        long windowStart;
        int failures;
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isBlocked(String username) {
        Entry e = entries.get(key(username));
        if (e == null) return false;
        synchronized (e) {
            if (System.currentTimeMillis() - e.windowStart > WINDOW_MS) return false;
            return e.failures >= MAX_FAILURES;
        }
    }

    public void recordFailure(String username) {
        if (entries.size() > MAX_TRACKED) {
            long now = System.currentTimeMillis();
            entries.values().removeIf(e -> now - e.windowStart > WINDOW_MS);
        }
        Entry e = entries.computeIfAbsent(key(username), k -> new Entry());
        synchronized (e) {
            long now = System.currentTimeMillis();
            if (now - e.windowStart > WINDOW_MS) {
                e.windowStart = now;
                e.failures = 0;
            }
            e.failures++;
        }
    }

    public void clear(String username) {
        entries.remove(key(username));
    }
}

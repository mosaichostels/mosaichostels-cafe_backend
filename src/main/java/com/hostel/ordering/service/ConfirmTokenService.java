package com.hostel.ordering.service;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-issued, single-use confirmation tokens for destructive bulk actions. The earlier
 * "token" was a client-chosen timestamp, which proved nothing: any recent number passed.
 * In-memory is enough - a token lives 30 seconds, and a restart simply asks the admin again.
 */
@Service
public class ConfirmTokenService {

    private record Grant(String scope, long expiresAt) {}

    private final ConcurrentHashMap<String, Grant> grants = new ConcurrentHashMap<>();
    private final long ttlMs;

    public ConfirmTokenService() {
        this(30_000);
    }

    ConfirmTokenService(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public String issue(String scope) {
        long now = System.currentTimeMillis();
        grants.values().removeIf(g -> g.expiresAt() <= now);
        String token = UUID.randomUUID().toString();
        grants.put(token, new Grant(scope, now + ttlMs));
        return token;
    }

    /** True once per issued token, only for the scope it was issued for and before it expires. */
    public boolean consume(String scope, String token) {
        if (token == null) return false;
        Grant g = grants.remove(token);
        return g != null && g.scope().equals(scope) && g.expiresAt() > System.currentTimeMillis();
    }

    public long ttlMs() {
        return ttlMs;
    }
}

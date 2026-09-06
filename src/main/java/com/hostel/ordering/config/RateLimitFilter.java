package com.hostel.ordering.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // ponytail: global lock, per-account locks if throughput matters
    private static final ConcurrentHashMap<String, RateLimit> limits = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60000;
    private static final int MAX_TRACKED = 10000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        int limit = -1;
        if ("POST".equals(method) && path.equals("/orders")) {
            limit = 5;
        } else if ("POST".equals(method) && path.equals("/auth/login")) {
            limit = 5;
        }

        if (limit > 0) {
            String limitKey = getRateLimitKey(request, method, path);
            String key = method + ":" + path + ":" + limitKey;

            // Lazy eviction: purge stale buckets when map grows too large
            if (limits.size() > MAX_TRACKED) {
                limits.values().removeIf(rl -> rl.isStale(WINDOW_MS));
            }

            RateLimit rl = limits.computeIfAbsent(key, k -> new RateLimit());
            if (!rl.allowRequest(limit, WINDOW_MS)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"errorCode\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Try again in 1 minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getRateLimitKey(HttpServletRequest request, String method, String path) {
        // Use username from JWT if authenticated (more reliable than IP behind proxy)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            return "user:" + auth.getName();
        }
        // Fall back to IP for unauthenticated routes (e.g., /orders POST by guests)
        return getClientIp(request);
    }

    String getClientIp(HttpServletRequest request) {
        // Trust X-Forwarded-For header ONLY when the direct peer is a private/loopback address
        // (indicating a trusted proxy). If remoteAddr is public, it's a direct peer and the
        // header is untrustworthy. Walk X-Forwarded-For right-to-left to find the first public
        // IP (rightmost entries appended by trusted infrastructure, leftmost entries client-sent).
        String remoteAddr = request.getRemoteAddr();

        // If direct peer is public, there is no trusted proxy in front; return it unchanged.
        if (!isPrivateOrLoopback(remoteAddr)) {
            return remoteAddr;
        }

        // Direct peer is private/loopback (trusted proxy). Read X-Forwarded-For.
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.trim().isEmpty()) {
            return remoteAddr;
        }

        // Split on "," and walk right-to-left for first non-private public IP.
        String[] entries = xff.split(",");
        for (int i = entries.length - 1; i >= 0; i--) {
            String ip = entries[i].trim();
            if (!ip.isEmpty() && !isPrivateOrLoopback(ip)) {
                return ip;
            }
        }

        // All entries are private/unparseable; return the direct peer.
        return remoteAddr;
    }

    private boolean isPrivateOrLoopback(String ip) {
        // Guard against DNS lookups on attacker input: only allow hex digits, dots, colons.
        if (!ip.matches("^[0-9a-fA-F:.]+$")) {
            return true; // Treat unparseable input as untrusted-for-use.
        }

        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() ||
                   addr.isLinkLocalAddress() || addr.isAnyLocalAddress();
        } catch (UnknownHostException e) {
            return true; // Treat unparseable input as untrusted-for-use.
        }
    }

    private static class RateLimit {
        long firstRequestTime;
        int count;

        synchronized boolean allowRequest(int maxRequests, long windowMs) {
            long now = System.currentTimeMillis();
            if (firstRequestTime == 0) {
                firstRequestTime = now;
                count = 1;
                return true;
            }
            if (now - firstRequestTime > windowMs) {
                firstRequestTime = now;
                count = 1;
                return true;
            }
            if (count < maxRequests) {
                count++;
                return true;
            }
            return false;
        }

        synchronized boolean isStale(long windowMs) {
            return firstRequestTime != 0 && System.currentTimeMillis() - firstRequestTime > windowMs;
        }
    }
}
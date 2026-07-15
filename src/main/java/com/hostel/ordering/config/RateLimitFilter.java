package com.hostel.ordering.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // ponytail: global lock, per-account locks if throughput matters
    private static final ConcurrentHashMap<String, RateLimit> limits = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        int limit = -1;
        if ("POST".equals(method) && path.equals("/orders")) {
            limit = 5;
        } else if ("POST".equals(method) && path.equals("/auth/login")) {
            limit = 10;
        }

        if (limit > 0) {
            String clientIp = getClientIp(request);
            String key = method + ":" + path + ":" + clientIp;

            RateLimit rl = limits.computeIfAbsent(key, k -> new RateLimit());
            if (!rl.allowRequest(limit, WINDOW_MS)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Rate limit exceeded");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
    }
}
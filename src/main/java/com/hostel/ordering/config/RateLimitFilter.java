package com.hostel.ordering.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RateLimitFilter implements Filter {

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final long WINDOW_MS = 60_000;

    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);

        if (isRateLimited(clientIp)) {
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String clientIp) {
        long now = System.currentTimeMillis();
        RateLimitEntry entry = rateLimitMap.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                return new RateLimitEntry(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return entry.count.get() > MAX_REQUESTS_PER_MINUTE;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateLimitEntry {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(1);

        RateLimitEntry(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
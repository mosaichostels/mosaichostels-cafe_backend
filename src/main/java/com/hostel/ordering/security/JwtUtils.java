package com.hostel.ordering.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${config.jwtSecret:}")
    private String jwtSecret;

    // 1 hour (3600000 ms) - reduced from 7 days
    @Value("${config.jwtExpirationMs:3600000}")
    private long jwtExpirationMs;

    // Token blacklist for logout - in-memory Set (could use Redis for multi-server)
    private static final Set<String> tokenBlacklist = ConcurrentHashMap.newKeySet();

    // Maximum staleness allowed for token refresh (24 hours in milliseconds)
    private static final long REFRESH_GRACE_MS = 24 * 60 * 60 * 1000L;

    @jakarta.annotation.PostConstruct
    void checkSecret() {
        if (jwtSecret == null || jwtSecret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "config.jwtSecret must be set (env CONFIG_JWTSECRET) and be at least 32 bytes");
        }
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        return Jwts.builder()
                .setSubject((userPrincipal.getUsername()))
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        // Check if token is blacklisted (logged out)
        if (isTokenBlacklisted(authToken)) {
            logger.error("JWT token is blacklisted (logged out)");
            return false;
        }

        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (SecurityException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    // Extract username from token even if expired (for refresh endpoint)
    public String getUserNameFromExpiredToken(String token) throws ExpiredJwtException {
        try {
            return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody().getSubject();
        } catch (ExpiredJwtException e) {
            // If token is expired, try to extract claims anyway
            return e.getClaims().getSubject();
        }
    }

    // Get expiration time from token (works for both valid and expired tokens)
    public long getExpirationMillisFromToken(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(token).getBody().getExpiration().getTime();
        } catch (ExpiredJwtException e) {
            // If token is expired, extract expiration from claims anyway
            return e.getClaims().getExpiration().getTime();
        }
    }

    // Check if token can be refreshed (not blacklisted, not too stale)
    public boolean isRefreshable(String token) {
        try {
            // Check if token is blacklisted
            if (isTokenBlacklisted(token)) {
                return false;
            }

            // Get expiration time and check staleness
            long expirationMs = getExpirationMillisFromToken(token);
            long timeSinceExpiration = System.currentTimeMillis() - expirationMs;

            // Token can be refreshed if it has not been expired for more than grace period
            // A still-valid (unexpired) token yields negative difference, which is refreshable
            return timeSinceExpiration <= REFRESH_GRACE_MS;
        } catch (Exception e) {
            // Any parse or signature failure means token is not refreshable
            return false;
        }
    }

    // Remove expired tokens from blacklist to prevent unbounded growth
    private void pruneBlacklist() {
        long now = System.currentTimeMillis();
        tokenBlacklist.removeIf(token -> {
            try {
                long expirationMs = getExpirationMillisFromToken(token);
                // Remove if token expired more than grace period ago
                return (expirationMs + REFRESH_GRACE_MS) < now;
            } catch (Exception e) {
                // Remove tokens that fail to parse
                return true;
            }
        });
    }

    // Add token to blacklist (for logout)
    public void blacklistToken(String token) {
        tokenBlacklist.add(token);
        logger.info("Token added to blacklist");

        // Prune expired entries when blacklist grows too large
        if (tokenBlacklist.size() > 1000) {
            pruneBlacklist();
        }
    }

    // Check if token is blacklisted
    public boolean isTokenBlacklisted(String token) {
        return tokenBlacklist.contains(token);
    }
}

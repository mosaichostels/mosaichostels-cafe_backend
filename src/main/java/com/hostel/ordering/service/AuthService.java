package com.hostel.ordering.service;

import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import com.hostel.ordering.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    AuditService auditService;

    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        org.springframework.security.core.userdetails.User userDetails =
                (org.springframework.security.core.userdetails.User) authentication.getPrincipal();

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("username", userDetails.getUsername());
        response.put("roles", userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList()));

        log.info("User {} logged in successfully", userDetails.getUsername());
        auditService.logAction("LOGIN_SUCCESS", "User logged in: " + userDetails.getUsername());
        return response;
    }

    public Map<String, Object> refreshToken(String token) {
        // Check if token can be refreshed (not blacklisted, not too stale)
        if (!jwtUtils.isRefreshable(token)) {
            throw new IllegalArgumentException("Token cannot be refreshed");
        }

        // Try to validate token; if expired, extract username from expired claims
        String username;
        try {
            username = jwtUtils.getUserNameFromExpiredToken(token);
        } catch (Exception e) {
            log.error("Failed to refresh token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid token");
        }

        // Load user and generate new token
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // A logout raises this watermark, revoking every token issued before it. The
        // in-memory blacklist alone would not survive a backend restart.
        if (user.getTokensValidFrom() != null
                && jwtUtils.getIssuedAtMillisFromToken(token) < user.getTokensValidFrom()) {
            throw new IllegalArgumentException("Token was revoked by logout");
        }

        // Create authentication object for token generation
        Set<org.springframework.security.core.GrantedAuthority> authorities = new java.util.HashSet<>();
        user.getRoles().forEach(role -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(role)));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, authorities);

        String newToken = jwtUtils.generateJwtToken(authentication);

        // Blacklist the old token to prevent replay on refresh
        jwtUtils.blacklistToken(token);

        Map<String, Object> response = new HashMap<>();
        response.put("token", newToken);
        response.put("username", user.getUsername());
        response.put("roles", new java.util.ArrayList<>(user.getRoles()));

        log.info("Token refreshed for user {}", username);
        auditService.logAction("TOKEN_REFRESH", "Token refreshed for user: " + username);
        return response;
    }

    public void logout(String token) {
        try {
            String username = jwtUtils.getUserNameFromExpiredToken(token);
            jwtUtils.blacklistToken(token);
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setTokensValidFrom(System.currentTimeMillis());
                userRepository.save(user);
            });
            log.info("User {} logged out successfully", username);
            auditService.logAction("LOGOUT_SUCCESS", "User logged out: " + username);
        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid token");
        }
    }

    public void registerInitialAdmin(String username, String password) {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            User user = new User(username, encoder.encode(password), Set.of("ROLE_ADMIN"));
            userRepository.save(user);
            log.info("Initial admin registered: {}", username);
            auditService.logAction("INITIAL_ADMIN_REGISTERED", "Initial admin registered: " + username);
        } else if (!encoder.matches(password, existing.getPassword())) {
            // config.admin.password env var is the source of truth for the admin password
            existing.setPassword(encoder.encode(password));
            userRepository.save(existing);
            log.info("Admin password synced from config for: {}", username);
            auditService.logAction("ADMIN_PASSWORD_SYNCED", "Admin password updated from config: " + username);
        }
    }
}

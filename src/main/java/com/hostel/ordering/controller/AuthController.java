package com.hostel.ordering.controller;

import com.hostel.ordering.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                "errorCode", "INVALID_REQUEST",
                "message", "Username and password are required"
            ));
        }

        try {
            Map<String, Object> response = authService.login(username, password);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of(
                "errorCode", "INVALID_CREDENTIALS",
                "message", "Invalid username or password"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                "errorCode", "AUTH_FAILED",
                "message", "Authentication failed"
            ));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body) {
        String token = null;

        // Try to get token from Authorization header first
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        // Fallback to request body if header not provided
        else if (body != null && body.containsKey("token")) {
            token = body.get("token");
        }

        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of(
                "errorCode", "INVALID_REQUEST",
                "message", "Token required in Authorization header or request body"
            ));
        }

        try {
            Map<String, Object> response = authService.refreshToken(token);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of(
                "errorCode", "INVALID_TOKEN",
                "message", "Token is invalid or expired"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                "errorCode", "TOKEN_REFRESH_FAILED",
                "message", "Token refresh failed"
            ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of(
                "errorCode", "INVALID_REQUEST",
                "message", "Valid Authorization header required"
            ));
        }

        String token = authHeader.substring(7);
        try {
            authService.logout(token);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of(
                "errorCode", "INVALID_TOKEN",
                "message", "Token is invalid or expired"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                "errorCode", "LOGOUT_FAILED",
                "message", "Logout failed"
            ));
        }
    }
}

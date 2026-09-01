package com.hostel.ordering.controller;

import com.hostel.ordering.model.User;
import com.hostel.ordering.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    com.hostel.ordering.service.AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> userRequest) {
        String username = (String) userRequest.get("username");
        String password = (String) userRequest.get("password");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) userRequest.get("roles");

        try {
            User user = userService.createUser(username, password, Set.copyOf(roles));
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        User user = userService.getUserById(id);
        String username = (user != null) ? user.getUsername() : id;

        userService.deleteUser(id);

        auditService.logAction("DELETED_USER", "Deleted user account: " + username);

        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody Map<String, Object> userRequest) {
        String username = (String) userRequest.get("username");
        String password = (String) userRequest.get("password");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) userRequest.get("roles");

        try {
            User user = userService.updateUser(id, username, password, Set.copyOf(roles));

            auditService.logAction("MODIFIED_USER", "Updated credentials for user: " + username);

            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/register-fcm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> registerFcmToken(@PathVariable String id, @RequestBody Map<String, String> request) {
        String fcmToken = request.get("fcmToken");
        if (fcmToken == null || fcmToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "FCM token is required"));
        }
        try {
            User user = userService.updateFcmToken(id, fcmToken);
            return ResponseEntity.ok(Map.of("message", "FCM token registered successfully", "userId", user.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

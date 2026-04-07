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
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    com.hostel.ordering.service.AuditService auditService;

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping
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
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        User user = userService.getUserById(id);
        String username = (user != null) ? user.getUsername() : id;

        userService.deleteUser(id);

        auditService.logAction("DELETED_USER", "Deleted user account: " + username);

        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @PutMapping("/{id}")
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
}

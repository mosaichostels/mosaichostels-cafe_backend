package com.hostel.ordering.service;

import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Set;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    AuditService auditService;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_ADMIN", "ROLE_STAFF");
    private static final int MIN_PASSWORD_LENGTH = 8;

    // Accepts "STAFF" as well as "ROLE_STAFF"; anything else is refused rather than stored, since
    // an unknown role would silently give the account no permissions at all.
    private static Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        Set<String> normalized = new java.util.HashSet<>();
        for (String r : roles) {
            String role = r == null ? "" : r.trim().toUpperCase(java.util.Locale.ROOT);
            if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
            if (!ALLOWED_ROLES.contains(role)) {
                throw new IllegalArgumentException("Unknown role: " + r);
            }
            normalized.add(role);
        }
        return normalized;
    }

    private static void requireStrongEnough(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private void requireAnotherAdmin(User user, String message) {
        long remainingAdmins = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(user.getId()))
                .filter(u -> u.getRoles() != null && u.getRoles().contains("ROLE_ADMIN"))
                .count();
        if (remainingAdmins == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public User createUser(String username, String password, Set<String> roles) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        requireStrongEnough(password);
        Set<String> cleanRoles = normalizeRoles(roles);
        String cleanUsername = username.trim();
        if (userRepository.findByUsername(cleanUsername).isPresent()) {
            throw new IllegalArgumentException("Username is already taken!");
        }

        User user = new User(cleanUsername, encoder.encode(password), cleanRoles);
        User saved = userRepository.save(user);
        log.info("New user created: {}", saved.getUsername());
        auditService.logAction("USER_CREATED", "Created user: " + saved.getUsername() + " with roles " + saved.getRoles());
        return saved;
    }

    // One lock around every change that can remove an administrator: without it two admins
    // demoting each other both pass the "another admin exists" check. Single backend instance;
    // use a database-enforced guard if that ever changes.
    public synchronized void deleteUser(String id) {
        userRepository.findById(id).ifPresent(user -> {
            // Refuse to remove the last administrator. Every admin-only endpoint would become
            // unreachable, including user management itself, so there would be no way back in
            // short of editing the database by hand.
            if (user.getRoles() != null && user.getRoles().contains("ROLE_ADMIN")) {
                requireAnotherAdmin(user, "Cannot delete the last administrator. Create another admin first.");
            }
            userRepository.delete(user);
            log.info("User {} deleted successfully", user.getUsername());
            auditService.logAction("USER_DELETED", "Deleted user: " + user.getUsername());
        });
    }

    public synchronized User updateUser(String id, String newUsername, String newPassword, Set<String> roles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));

        if (newUsername == null || newUsername.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        String cleanUsername = newUsername.trim();
        Set<String> cleanRoles = normalizeRoles(roles);
        boolean changingPassword = newPassword != null && !newPassword.trim().isEmpty();
        if (changingPassword) {
            requireStrongEnough(newPassword);
        }

        if (!user.getUsername().equals(cleanUsername) && userRepository.existsByUsername(cleanUsername)) {
            throw new IllegalArgumentException("Username is already taken!");
        }

        boolean wasAdmin = user.getRoles() != null && user.getRoles().contains("ROLE_ADMIN");
        if (wasAdmin && !cleanRoles.contains("ROLE_ADMIN")) {
            requireAnotherAdmin(user, "Cannot remove the last administrator's admin role. Create another admin first.");
        }

        boolean credentialsChanged = changingPassword
                || !cleanRoles.equals(user.getRoles())
                || !user.getUsername().equals(cleanUsername);

        user.setUsername(cleanUsername);
        user.setRoles(cleanRoles);
        if (changingPassword) {
            user.setPassword(encoder.encode(newPassword));
        }
        // A changed password, role or name must not leave tokens issued under the old ones alive
        // for the year the refresh grace allows.
        if (credentialsChanged) {
            user.setTokensValidFrom(System.currentTimeMillis());
        }

        User saved = userRepository.save(user);
        log.info("User {} updated successfully", saved.getUsername());
        auditService.logAction("USER_UPDATED", "Updated user: " + saved.getUsername() + " with roles " + saved.getRoles());
        return saved;
    }

    public User updateFcmTokenByUsername(String username, String fcmToken) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));
        user.setFcmToken(fcmToken);
        User saved = userRepository.save(user);
        log.info("FCM token updated for user: {}", saved.getUsername());
        return saved;
    }
}

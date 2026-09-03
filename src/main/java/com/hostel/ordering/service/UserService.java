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

    public User createUser(String username, String password, Set<String> roles) {
        String cleanUsername = username.trim();
        if (userRepository.findByUsername(cleanUsername).isPresent()) {
            throw new IllegalArgumentException("Username is already taken!");
        }

        User user = new User(cleanUsername, encoder.encode(password), roles);
        User saved = userRepository.save(user);
        log.info("New user created: {}", saved.getUsername());
        auditService.logAction("USER_CREATED", "Created user: " + saved.getUsername() + " with roles " + saved.getRoles());
        return saved;
    }

    public void deleteUser(String id) {
        userRepository.findById(id).ifPresent(user -> {
            userRepository.delete(user);
            log.info("User {} deleted successfully", user.getUsername());
            auditService.logAction("USER_DELETED", "Deleted user: " + user.getUsername());
        });
    }

    public User updateUser(String id, String newUsername, String newPassword, Set<String> roles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));

        String cleanUsername = newUsername.trim();

        if (!user.getUsername().equals(cleanUsername) && userRepository.existsByUsername(cleanUsername)) {
            throw new IllegalArgumentException("Username is already taken!");
        }

        user.setUsername(cleanUsername);
        user.setRoles(roles);

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            user.setPassword(encoder.encode(newPassword));
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

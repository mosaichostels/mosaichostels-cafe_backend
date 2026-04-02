package com.hostel.ordering.service;

import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class UserService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User createUser(String username, String password, Set<String> roles) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken!");
        }

        User user = new User(username, encoder.encode(password), roles);
        return userRepository.save(user);
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    public User updateUser(String id, String newUsername, String newPassword, Set<String> roles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));

        // If username is changing, check if new one is taken
        if (!user.getUsername().equals(newUsername) && userRepository.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("Username is already taken!");
        }

        user.setUsername(newUsername);
        user.setRoles(roles);

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            user.setPassword(encoder.encode(newPassword));
        }

        return userRepository.save(user);
    }
}

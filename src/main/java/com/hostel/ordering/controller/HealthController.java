package com.hostel.ordering.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hostel.ordering.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired
    UserRepository userRepository;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean hasAdmin = userRepository.findByUsername("admin").isPresent();
        return ResponseEntity.ok(Map.of("status", "UP", "adminExists", hasAdmin));
    }
}

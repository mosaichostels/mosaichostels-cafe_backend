package com.hostel.ordering.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "Mosaic Hostels Cafe API"));
    }
}

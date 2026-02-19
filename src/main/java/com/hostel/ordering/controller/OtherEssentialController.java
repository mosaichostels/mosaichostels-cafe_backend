package com.hostel.ordering.controller;

import com.hostel.ordering.model.OtherEssential;
import com.hostel.ordering.service.OtherEssentialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/other-essentials")
public class OtherEssentialController {

    @Autowired
    private OtherEssentialService otherEssentialService;

    @PostMapping
    public ResponseEntity<?> createOtherEssential(@RequestBody OtherEssential otherEssential) {
        try {
            OtherEssential created = otherEssentialService.createOtherEssential(otherEssential);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating other essential: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOtherEssential(@PathVariable String id) {
        try {
            OtherEssential otherEssential = otherEssentialService.getOtherEssential(id);
            if (otherEssential != null) {
                return ResponseEntity.ok(otherEssential);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Other essential not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching other essential: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllOtherEssentials() {
        try {
            List<OtherEssential> otherEssentials = otherEssentialService.getAllOtherEssentials();
            return ResponseEntity.ok(otherEssentials);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching other essentials: " + e.getMessage());
        }
    }

    @GetMapping("/available")
    public ResponseEntity<?> getAvailableOtherEssentials() {
        try {
            List<OtherEssential> otherEssentials = otherEssentialService.getAvailableOtherEssentials();
            return ResponseEntity.ok(otherEssentials);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching available other essentials: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateOtherEssential(@PathVariable String id, @RequestBody OtherEssential otherEssential) {
        try {
            OtherEssential updated = otherEssentialService.updateOtherEssential(id, otherEssential);
            if (updated != null) {
                return ResponseEntity.ok(updated);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Other essential not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating other essential: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOtherEssential(@PathVariable String id) {
        try {
            otherEssentialService.deleteOtherEssential(id);
            return ResponseEntity.ok("Other essential deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting other essential: " + e.getMessage());
        }
    }
}

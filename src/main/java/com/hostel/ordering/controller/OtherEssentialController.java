package com.hostel.ordering.controller;

import com.hostel.ordering.model.OtherEssential;
import com.hostel.ordering.service.OtherEssentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/other-essentials")
@RequiredArgsConstructor
public class OtherEssentialController {

    private final OtherEssentialService otherEssentialService;

    @PostMapping
    public ResponseEntity<OtherEssential> createOtherEssential(@RequestBody OtherEssential otherEssential) {
        OtherEssential created = otherEssentialService.createOtherEssential(otherEssential);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OtherEssential> getOtherEssential(@PathVariable String id) {
        OtherEssential otherEssential = otherEssentialService.getOtherEssential(id);
        if (otherEssential != null) {
            return ResponseEntity.ok(otherEssential);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping
    public ResponseEntity<List<OtherEssential>> getAllOtherEssentials() {
        return ResponseEntity.ok(otherEssentialService.getAllOtherEssentials());
    }

    @GetMapping("/available")
    public ResponseEntity<List<OtherEssential>> getAvailableOtherEssentials() {
        return ResponseEntity.ok(otherEssentialService.getAvailableOtherEssentials());
    }

    @GetMapping("/search")
    public ResponseEntity<List<OtherEssential>> searchOtherEssentials(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "false") boolean availableOnly) {
        return ResponseEntity.ok(otherEssentialService.searchOtherEssentials(q, availableOnly));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OtherEssential> updateOtherEssential(@PathVariable String id, @RequestBody OtherEssential otherEssential) {
        OtherEssential updated = otherEssentialService.updateOtherEssential(id, otherEssential);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteOtherEssential(@PathVariable String id) {
        otherEssentialService.deleteOtherEssential(id);
        return ResponseEntity.ok("Other essential deleted successfully");
    }
}

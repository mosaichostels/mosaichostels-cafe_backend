package com.hostel.ordering.controller;

import com.hostel.ordering.model.OtherEssential;
import com.hostel.ordering.service.OtherEssentialService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/other-essentials")
public class OtherEssentialController {

    private final OtherEssentialService otherEssentialService;

    public OtherEssentialController(OtherEssentialService otherEssentialService) {
        this.otherEssentialService = otherEssentialService;
    }

    @PostMapping
    public ResponseEntity<OtherEssential> createOtherEssential(@RequestBody OtherEssential otherEssential) {
        return ResponseEntity.status(HttpStatus.CREATED).body(otherEssentialService.createOtherEssential(otherEssential));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OtherEssential> getOtherEssential(@PathVariable String id) {
        OtherEssential item = otherEssentialService.getOtherEssential(id);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
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
    public ResponseEntity<OtherEssential> updateOtherEssential(@PathVariable String id,
                                                               @RequestBody OtherEssential otherEssential) {
        OtherEssential updated = otherEssentialService.updateOtherEssential(id, otherEssential);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteOtherEssential(@PathVariable String id) {
        otherEssentialService.deleteOtherEssential(id);
        return ResponseEntity.ok("Other essential deleted successfully");
    }
}

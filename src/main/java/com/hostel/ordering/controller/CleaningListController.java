package com.hostel.ordering.controller;

import com.hostel.ordering.model.CleaningListEntry;
import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.CleaningListService;
import com.hostel.ordering.service.CleaningListService.BedInput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cleaning-list")
public class CleaningListController {

    private static final Logger log = LoggerFactory.getLogger(CleaningListController.class);

    private final CleaningListService service;
    private final AuditService auditService;

    public CleaningListController(CleaningListService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HOUSEKEEPING')")
    public Map<String, Object> list() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (CleaningListEntry e : service.list()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("roomId", e.getRoomId());
            m.put("unitId", e.getUnitId());
            m.put("roomName", e.getRoomName());
            m.put("addedBy", e.getAddedBy());
            m.put("addedAt", e.getAddedAt());
            items.add(m);
        }
        return Map.of("items", items);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body, Authentication authentication) {
        if (!(body.get("beds") instanceof List<?> raw)) {
            throw new IllegalArgumentException("beds is required");
        }
        List<BedInput> beds = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("Invalid bed");
            }
            beds.add(new BedInput(str(m.get("roomId")), str(m.get("unitId")), str(m.get("roomName"))));
        }
        String day = body.get("day") == null ? null : str(body.get("day"));
        if (body.get("day") != null && day == null) {
            throw new IllegalArgumentException("day must be yyyy-MM-dd");
        }
        Integer taskCount = null;
        if (body.get("taskCount") != null) {
            if (!(body.get("taskCount") instanceof Integer n)) {
                throw new IllegalArgumentException("taskCount must be a whole number");
            }
            taskCount = n;
        }
        var r = service.submit(beds, authentication.getName(), day, taskCount);
        audit("CLEANING_LIST_SUBMITTED", "added " + r.added() + " beds");
        if (r.daySubmitted()) {
            audit("SCHEDULE_DAY_SUBMITTED", "submitted the schedule for " + day);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("daySubmitted", r.daySubmitted());
        out.put("added", r.added());
        out.put("alreadyListed", r.alreadyListed());
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> remove(@PathVariable String id, Authentication authentication) {
        if (!service.remove(id)) {
            return ResponseEntity.status(404).body(Map.of("message", "Bed is not on the list"));
        }
        audit("CLEANING_BED_REMOVED", "removed a bed from the cleaning list (id " + id + ")");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/done")
    @PreAuthorize("hasAnyRole('ADMIN','HOUSEKEEPING')")
    public ResponseEntity<?> done(@PathVariable String id, Authentication authentication) {
        var r = service.done(id, authentication.getName());
        if (!r.alreadyDone()) {
            audit("CLEANING_BED_DONE", r.roomName() + " cleaned (eZee " + r.ezee() + ")");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("alreadyDone", r.alreadyDone());
        out.put("ezee", r.ezee());
        out.put("roomName", r.roomName());
        return ResponseEntity.ok(out);
    }

    // The change is already made; an audit failure must not turn it into a 500.
    private void audit(String action, String details) {
        try {
            auditService.logAction(action, details);
        } catch (RuntimeException e) {
            log.warn("Audit {} failed: {}", action, e.toString());
        }
    }

    // Non-string ids/names are rejected by the service's validation (null fails every check).
    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }
}

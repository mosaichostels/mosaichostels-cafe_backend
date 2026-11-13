package com.hostel.ordering.controller;

import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.ScheduleService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);

    private final ScheduleService service;
    private final AuditService auditService;

    public ScheduleController(ScheduleService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping("/days")
    @PreAuthorize("hasAnyRole('ADMIN','HOUSEKEEPING')")
    public Map<String, Object> days(@RequestParam String from, @RequestParam String to) {
        List<Map<String, Object>> out = service.days(from, to).stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.date());
            m.put("submitted", d.submitted());
            m.put("submittedAt", d.submittedAt());
            m.put("ticks", d.ticks().stream().map(t -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("taskKey", t.getTaskKey());
                x.put("slot", t.getSlot());
                x.put("doneBy", t.getDoneBy());
                x.put("doneAt", t.getDoneAt());
                x.put("confirmedBy", t.getConfirmedBy());
                x.put("confirmedAt", t.getConfirmedAt());
                return x;
            }).toList());
            return m;
        }).toList();
        return Map.of("days", out);
    }

    @PostMapping("/days/{date}/ticks")
    @PreAuthorize("hasAnyRole('ADMIN','HOUSEKEEPING')")
    public Map<String, Object> tick(@PathVariable String date, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        var r = service.tick(date, str(body.get("taskKey")), str(body.get("slot")), authentication.getName());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alreadyTicked", r.alreadyTicked());
        out.put("doneBy", r.doneBy());
        return out;
    }

    @PostMapping("/days/{date}/ticks/{taskKey}/{slot}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> confirm(@PathVariable String date, @PathVariable String taskKey,
            @PathVariable String slot, Authentication authentication) {
        if (!service.confirm(date, taskKey, slot, authentication.getName())) {
            return ResponseEntity.status(404).body(Map.of("message", "That task has not been ticked"));
        }
        try {
            auditService.logAction("SCHEDULE_TASK_CONFIRMED", "confirmed " + taskKey + " (" + slot + ") for " + date);
        } catch (RuntimeException e) {
            log.warn("Audit SCHEDULE_TASK_CONFIRMED failed: {}", e.toString());
        }
        return ResponseEntity.ok(Map.of("confirmed", true));
    }

    // Non-string values fail the service's validation (null is rejected there).
    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }
}

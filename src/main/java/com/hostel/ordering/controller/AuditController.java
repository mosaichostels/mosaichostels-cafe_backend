package com.hostel.ordering.controller;

import com.hostel.ordering.model.AuditLog;
import com.hostel.ordering.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    @Autowired
    AuditService auditService;

    @GetMapping
    public List<AuditLog> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long dateFrom,
            @RequestParam(required = false) Long dateTo,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        if (action == null && username == null && dateFrom == null && dateTo == null) {
            return auditService.getFilteredLogs(null, null, null, null, limit, offset);
        }
        return auditService.getFilteredLogs(action, username, dateFrom, dateTo, limit, offset);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteAllAuditLogs(
            @RequestParam(required = false) String confirmToken,
            Authentication authentication) {
        if (confirmToken == null || confirmToken.isEmpty()) {
            // Return instruction to get token first
            long tokenExpiry = System.currentTimeMillis() + 30000; // 30 second window
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Confirmation required for delete-all");
            response.put("requiresConfirmation", true);
            response.put("tokenExpiry", tokenExpiry);
            return ResponseEntity.status(400).body(response);
        }

        // Verify token (in production, validate against server-side generated token)
        // This is a simplified check; ideally use a proper token store
        try {
            long tokenTimestamp = Long.parseLong(confirmToken);
            long now = System.currentTimeMillis();
            if (now - tokenTimestamp > 30000 || tokenTimestamp > now) {
                throw new IllegalArgumentException("Confirmation token expired or invalid");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid confirmation token format");
        }

        String auditedBy = authentication != null ? authentication.getName() : "UNKNOWN";
        auditService.deleteAllLogs();

        // Add audit trail
        String message = "Admin deleted ALL audit logs - User: " + auditedBy + ", Timestamp: " + System.currentTimeMillis();
        auditService.logAction("DELETE_ALL_AUDIT_LOGS", message);

        return ResponseEntity.ok("All audit logs deleted successfully (admin: " + auditedBy + ")");
    }
}

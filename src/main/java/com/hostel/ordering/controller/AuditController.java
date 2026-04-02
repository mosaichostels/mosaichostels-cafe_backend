package com.hostel.ordering.controller;

import com.hostel.ordering.model.AuditLog;
import com.hostel.ordering.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    @Autowired
    AuditService auditService;

    @GetMapping
    public List<AuditLog> getAuditLogs() {
        return auditService.getAllLogs();
    }
}

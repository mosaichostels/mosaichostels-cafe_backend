package com.hostel.ordering.service;

import com.hostel.ordering.model.AuditLog;
import com.hostel.ordering.repository.AuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    @Autowired
    AuditRepository auditRepository;

    public void logAction(String action, String details) {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? ((UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername()
                : "SYSTEM";

        AuditLog log = AuditLog.builder()
                .username(username)
                .action(action)
                .details(details)
                .timestamp(System.currentTimeMillis())
                .build();

        auditRepository.save(log);
    }

    public List<AuditLog> getAllLogs() {
        return auditRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
    }
}

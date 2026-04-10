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
        String username = "SYSTEM";
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetails) {
                username = ((UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                username = (String) principal;
                if ("anonymousUser".equals(username)) {
                    username = "GUEST";
                }
            }
        }

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

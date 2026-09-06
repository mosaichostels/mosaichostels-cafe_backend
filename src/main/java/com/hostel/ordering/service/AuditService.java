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

    public List<AuditLog> getFilteredLogs(String action, String username, Long dateFrom, Long dateTo, Integer limit, Integer offset) {
        List<AuditLog> logs = getAllLogs();

        if (action != null && !action.isBlank()) {
            String lowerAction = action.toLowerCase();
            logs = logs.stream().filter(l -> l.getAction().toLowerCase().contains(lowerAction)).toList();
        }
        if (username != null && !username.isBlank()) {
            String lowerUsername = username.toLowerCase();
            logs = logs.stream().filter(l -> l.getUsername().toLowerCase().contains(lowerUsername)).toList();
        }
        if (dateFrom != null) {
            logs = logs.stream().filter(l -> l.getTimestamp() >= dateFrom).toList();
        }
        if (dateTo != null) {
            logs = logs.stream().filter(l -> l.getTimestamp() <= dateTo).toList();
        }

        int off = offset != null && offset > 0 ? offset : 0;
        int lim = limit != null && limit > 0 ? Math.min(limit, 1000) : 100;
        return logs.stream().skip(off).limit(lim).toList();
    }

    public void deleteAllLogs() {
        auditRepository.deleteAll();
    }
}

package com.hostel.ordering.service;

import com.hostel.ordering.model.AuditLog;
import com.hostel.ordering.repository.AuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.regex.Pattern;
@Service
public class AuditService {

    @Autowired
    AuditRepository auditRepository;

    @Autowired
    MongoTemplate mongoTemplate;

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

    public List<AuditLog> getFilteredLogs(String action, String username, Long dateFrom, Long dateTo, Integer limit, Integer offset) {
        Query query = new Query();
        if (action != null && !action.isBlank()) {
            query.addCriteria(Criteria.where("action").regex(Pattern.compile(Pattern.quote(action), Pattern.CASE_INSENSITIVE)));
        }
        if (username != null && !username.isBlank()) {
            query.addCriteria(Criteria.where("username").regex(Pattern.compile(Pattern.quote(username), Pattern.CASE_INSENSITIVE)));
        }
        if (dateFrom != null || dateTo != null) {
            Criteria timestamp = Criteria.where("timestamp");
            if (dateFrom != null) timestamp.gte(dateFrom);
            if (dateTo != null) timestamp.lte(dateTo);
            query.addCriteria(timestamp);
        }

        int off = offset != null && offset > 0 ? offset : 0;
        int lim = limit != null && limit > 0 ? Math.min(limit, 1000) : 100;
        query.with(Sort.by(Sort.Direction.DESC, "timestamp")).skip(off).limit(lim);
        return mongoTemplate.find(query, AuditLog.class);
    }

    public List<String> getActions() {
        return mongoTemplate.findDistinct(new Query(), "action", AuditLog.class, String.class).stream()
                .sorted()
                .toList();
    }

    public void deleteAllLogs() {
        auditRepository.deleteAll();
    }
}

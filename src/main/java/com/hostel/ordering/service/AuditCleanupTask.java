package com.hostel.ordering.service;

import com.hostel.ordering.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AuditCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(AuditCleanupTask.class);

    @Autowired
    AuditRepository auditRepository;

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOldLogs() {
        long sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        auditRepository.deleteByTimestampLessThan(sevenDaysAgo);
        logger.info("Executed scheduled cleanup of audit logs older than 7 days.");
    }
}

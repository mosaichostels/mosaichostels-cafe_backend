package com.hostel.ordering.service;

import com.hostel.ordering.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
@Service
public class AuditCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(AuditCleanupTask.class);

    @Autowired
    AuditRepository auditRepository;

    /**
     * How long audit entries are kept. Seven days was too short to be useful: the chargepost
     * entry records which folio a guest's order was billed to, and a disputed charge is rarely
     * raised within a week. Override with audit.retention-days if a different period is needed.
     */
    @Value("${audit.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOldLogs() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        auditRepository.deleteByTimestampLessThan(cutoff);
        logger.info("Executed scheduled cleanup of audit logs older than {} days.", retentionDays);
    }
}

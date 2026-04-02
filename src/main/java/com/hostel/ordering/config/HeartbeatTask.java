package com.hostel.ordering.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * Ensures the application is not considered "Idle" by Oracle Cloud's reclamation policy.
 * This logs a tiny heartbeat every 6 hours to provide a trace of system activity.
 */
@Configuration
@EnableScheduling
@Slf4j
public class HeartbeatTask {

    // Runs every 6 hours to keep the instance active
    @Scheduled(fixedRate = 21600000)
    public void serverHeartbeat() {
        log.info("System Heartbeat (Keep-Alive) active at {}", LocalDateTime.now());
    }
}

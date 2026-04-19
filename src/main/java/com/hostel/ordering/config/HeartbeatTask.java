package com.hostel.ordering.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;

@Configuration
@EnableScheduling
public class HeartbeatTask {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatTask.class);

    @Scheduled(fixedRate = 21600000)
    public void serverHeartbeat() {
        log.info("System Heartbeat (Keep-Alive) active at {}", LocalDateTime.now());
    }
}

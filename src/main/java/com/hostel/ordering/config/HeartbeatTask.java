package com.hostel.ordering.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
@Configuration
@EnableScheduling
@Slf4j
public class HeartbeatTask {

    @Scheduled(fixedRate = 21600000)
    public void serverHeartbeat() {
        log.info("System Heartbeat (Keep-Alive) active at {}", LocalDateTime.now());
    }
}

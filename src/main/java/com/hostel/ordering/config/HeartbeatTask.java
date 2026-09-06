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

    /**
     * Liveness marker in the log, nothing more.
     *
     * <p>It was named "Keep-Alive" and the README still claims the Space is kept awake by
     * heartbeats. It is not: a Hugging Face Space sleeps on absence of *inbound* HTTP traffic,
     * which no amount of internal logging or a localhost HEALTHCHECK can supply. Keeping the
     * Space awake needs an external pinger hitting the public URL.
     */
    @Scheduled(fixedRate = 21600000)
    public void serverHeartbeat() {
        log.info("Liveness marker at {} (does not keep the Space awake)", LocalDateTime.now());
    }
}

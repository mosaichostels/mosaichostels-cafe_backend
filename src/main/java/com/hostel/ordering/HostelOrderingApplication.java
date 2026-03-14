package com.hostel.ordering;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;

@SpringBootApplication
@Slf4j
public class HostelOrderingApplication {

    public static void main(String[] args) {
        // Validate critical env vars before Spring attempts to connect
        String mongoUri = System.getenv("MONGODB_URI");
        if (mongoUri == null || mongoUri.isBlank()) {
            System.err.println("❌ ERROR: MONGODB_URI environment variable is not set! Exiting.");
            // Exit immediately with a clear error rather than looping with OOM
            System.exit(1);
        } else {
            System.out.println("✅ MONGODB_URI is present.");
        }

        SpringApplication app = new SpringApplication(HostelOrderingApplication.class);
        app.addListeners((ApplicationListener<ApplicationFailedEvent>) event ->
            log.error("❌ Application failed to start!", event.getException())
        );
        app.run(args);
        // No CommandLineRunner bean — that extra bean init adds unnecessary overhead at startup
        log.info("🚀 Hostel Ordering Application started successfully.");
    }
}

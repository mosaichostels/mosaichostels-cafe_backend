package com.hostel.ordering;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class HostelOrderingApplication {

    public static void main(String[] args) {
        String mongoUri = System.getenv("MONGODB_URI");
        if (mongoUri == null || mongoUri.isBlank()) {
            System.err.println("❌ ERROR: MONGODB_URI environment variable is not set!");
        } else {
            System.out.println("✅ MONGODB_URI is present.");
        }

        SpringApplication app = new SpringApplication(HostelOrderingApplication.class);
        app.addListeners((ApplicationListener<ApplicationFailedEvent>) event -> {
            log.error("❌ CRITICAL: Application failed to start!", event.getException());
        });
        app.run(args);
    }

    @Bean
    public CommandLineRunner startupSuccess() {
        return args -> log.info("🚀 SUCCESS: Hostel Ordering Application is fully initialized and ready!");
    }
}

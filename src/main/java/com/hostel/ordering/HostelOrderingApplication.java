package com.hostel.ordering;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.hostel.ordering.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class HostelOrderingApplication {

    @Value("${config.admin.username:admin}")
    private String adminUsername;

    @Value("${config.admin.password:}")
    private String adminPassword;

    public static void main(String[] args) {
        SpringApplication.run(HostelOrderingApplication.class, args);
    }

    @Bean
    CommandLineRunner init(AuthService authService) {
        return args -> {
            String passwordToUse = (adminPassword != null && !adminPassword.isBlank())
                ? adminPassword
                : "admin123";
            System.out.println("[BOOTSTRAP] Initializing admin user with username=" + adminUsername);
            try {
                authService.registerInitialAdmin(adminUsername, passwordToUse);
                System.out.println("[BOOTSTRAP] Admin initialization complete");
            } catch (Exception e) {
                System.out.println("[BOOTSTRAP] Admin initialization failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}

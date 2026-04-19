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

    @Value("${config.admin.password:admin123}")
    private String adminPassword;

    public static void main(String[] args) {
        SpringApplication.run(HostelOrderingApplication.class, args);
    }

    @Bean
    CommandLineRunner init(AuthService authService) {
        return args -> {
            try {
                authService.registerInitialAdmin(adminUsername, adminPassword);
            } catch (Exception e) {
                System.out.println("Admin initialization skipped: " + e.getMessage());
            }
        };
    }
}

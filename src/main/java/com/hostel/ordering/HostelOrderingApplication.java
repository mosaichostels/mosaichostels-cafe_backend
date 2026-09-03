package com.hostel.ordering;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.hostel.ordering.service.AuthService;
import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class HostelOrderingApplication {

    @Value("${config.admin.username:admin}")
    private String adminUsername;

    @Value("${config.admin.password:#{null}}")
    private String adminPassword;

    public static void main(String[] args) {
        SpringApplication.run(HostelOrderingApplication.class, args);
    }

    @Bean
    CommandLineRunner init(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (adminPassword == null || adminPassword.isBlank()) {
                System.out.println("[BOOTSTRAP] WARN: config.admin.password is not set; skipping default admin creation");
                return;
            }
            System.out.println("[BOOTSTRAP] Initializing admin user: " + adminUsername);
            try {
                var existing = userRepository.findByUsername(adminUsername);
                if (existing.isEmpty()) {
                    User user = new User(adminUsername, passwordEncoder.encode(adminPassword), java.util.Set.of("ROLE_ADMIN"));
                    userRepository.save(user);
                    System.out.println("[BOOTSTRAP] Created admin user");
                } else {
                    System.out.println("[BOOTSTRAP] Admin user already exists");
                }
            } catch (Exception e) {
                System.out.println("[BOOTSTRAP] Error: " + e.getMessage());
            }
        };
    }
}

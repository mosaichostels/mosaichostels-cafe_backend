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

    /**
     * Delegates to {@link AuthService#registerInitialAdmin}, which treats config.admin.password
     * as the source of truth and re-encodes it when the stored hash no longer matches.
     *
     * <p>This used to be a create-only copy of that logic inlined here, which meant changing
     * CONFIG_ADMIN_PASSWORD had no effect on an existing account: the runner logged "already
     * exists" and left the old hash in place, so the only way back in was to delete the user
     * row by hand and restart.
     */
    @Bean
    CommandLineRunner init(AuthService authService) {
        return args -> {
            if (adminPassword == null || adminPassword.isBlank()) {
                System.out.println("[BOOTSTRAP] WARN: config.admin.password is not set; skipping default admin creation");
                return;
            }
            System.out.println("[BOOTSTRAP] Initializing admin user: " + adminUsername);
            try {
                authService.registerInitialAdmin(adminUsername, adminPassword);
                System.out.println("[BOOTSTRAP] Admin user ensured: " + adminUsername);
            } catch (Exception e) {
                System.out.println("[BOOTSTRAP] Error: " + e.getMessage());
            }
        };
    }
}

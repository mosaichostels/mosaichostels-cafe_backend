package com.hostel.ordering.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves the real RoleHierarchy bean is applied by @PreAuthorize, not just unit-correct. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RoleHierarchyMethodSecurityTest.Cfg.class)
class RoleHierarchyMethodSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        Guarded guarded() {
            return new Guarded();
        }
    }

    static class Guarded {
        @PreAuthorize("hasRole('ADMIN')")
        public void adminOnly() {}

        @PreAuthorize("hasRole('SUPERADMIN')")
        public void superOnly() {}
    }

    @Autowired
    Guarded guarded;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
    }

    @Test
    void superAdminPassesAdminCheck() {
        as("ROLE_SUPERADMIN");
        assertDoesNotThrow(guarded::adminOnly);
        assertDoesNotThrow(guarded::superOnly);
    }

    @Test
    void adminDeniedSuperAdminCheck() {
        as("ROLE_ADMIN");
        assertDoesNotThrow(guarded::adminOnly);
        assertThrows(AccessDeniedException.class, guarded::superOnly);
    }

    @Test
    void staffDeniedBoth() {
        as("ROLE_STAFF");
        assertThrows(AccessDeniedException.class, guarded::adminOnly);
        assertThrows(AccessDeniedException.class, guarded::superOnly);
    }
}

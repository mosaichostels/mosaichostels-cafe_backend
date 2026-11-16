package com.hostel.ordering.pms;

import com.hostel.ordering.config.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PmsHousekeepingControllerTest.Cfg.class)
class PmsHousekeepingControllerTest {

    static final PmsHousekeepingService SERVICE = mock(PmsHousekeepingService.class);

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        PmsHousekeepingController controller() {
            return new PmsHousekeepingController(SERVICE);
        }
    }

    @Autowired
    PmsHousekeepingController controller;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roomFeedAllowsHousekeepingAndAdmins() {
        when(SERVICE.rooms()).thenReturn(new Cached<>(java.util.List.of(), 5L, false));
        for (String role : new String[]{"ROLE_HOUSEKEEPING", "ROLE_ADMIN", "ROLE_SUPERADMIN"}) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
            assertEquals(5L, controller.rooms().asOf());
        }
    }

    @Test
    void roomFeedRefusesUnauthenticatedAndOtherRoles() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.rooms());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", "ROLE_KITCHEN"));
        assertThrows(AccessDeniedException.class, () -> controller.rooms());
    }
}

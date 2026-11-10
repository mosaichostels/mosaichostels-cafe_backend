package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.config.SecurityConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Runs the controller through real method security with the real role hierarchy. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PmsStatusControllerTest.Cfg.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PmsStatusControllerTest {

    // not beans: Spring would try to autowire the mocks' inherited @Autowired fields
    static final EzeeGateway GATEWAY = mock(EzeeGateway.class);
    static final PmsRateLimiter LIMITER = mock(PmsRateLimiter.class);
    static final PmsProperties PROPS = new PmsProperties();

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        PmsStatusController controller() {
            return new PmsStatusController(PROPS, new PmsFlags(PROPS), new PmsCache(), GATEWAY, LIMITER);
        }
    }

    @Autowired
    PmsStatusController controller;

    @BeforeEach
    void setUp() {
        reset(GATEWAY, LIMITER);
        PROPS.setEnabled(true);
        PROPS.setWrites(Map.of("room-status", true, "housekeeping", false));
        PROPS.setCanary(Map.of("room-status", List.of("SECRET-CANARY-ID")));
        PROPS.setTtl(Map.of());
        when(LIMITER.usedInWindow()).thenReturn(2);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
    }

    @Test
    void superAdminGetsExactShape() throws Exception {
        as("ROLE_SUPERADMIN");
        PmsStatusController.Status s = controller.status();
        assertEquals("OK", s.eZee());
        assertTrue(s.enabled());
        assertFalse(s.stale());
        assertEquals(2, s.limiterUsed());
        assertEquals(Map.of("housekeeping", false, "room-status", true), s.writeFlags());
        assertTrue(s.checkedAt() > 0);
        String json = new ObjectMapper().writeValueAsString(s);
        assertEquals(List.of("enabled", "eZee", "checkedAt", "stale", "limiterUsed", "writeFlags"),
                new ObjectMapper().readTree(json).properties().stream().map(Map.Entry::getKey).toList());
        assertFalse(json.contains("SECRET-CANARY-ID"));
    }

    @Test
    void adminAndStaffGet403() {
        as("ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, controller::status);
        as("ROLE_STAFF");
        assertThrows(AccessDeniedException.class, controller::status);
        verifyNoInteractions(GATEWAY);
    }

    @Test
    void unauthenticatedIsRefused() {
        // over HTTP the filter chain answers 401 first (SecurityConfig: anyRequest().authenticated())
        assertThrows(AuthenticationCredentialsNotFoundException.class, controller::status);
    }

    @Test
    void authFailureIsReportedNotThrown() {
        as("ROLE_SUPERADMIN");
        when(GATEWAY.json(any(), any(), any(), any())).thenThrow(new PmsAuthException("bad"));
        PmsStatusController.Status s = controller.status();
        assertEquals("AUTH", s.eZee());
        assertFalse(s.stale());
    }

    @Test
    void otherFailureIsUpstream() {
        as("ROLE_SUPERADMIN");
        when(GATEWAY.json(any(), any(), any(), any())).thenThrow(new PmsUpstreamException("down"));
        assertEquals("UPSTREAM", controller.status().eZee());
    }

    @Test
    void disabledMakesNoProbeAndNoLimiterSlot() {
        as("ROLE_SUPERADMIN");
        PROPS.setEnabled(false);
        PmsStatusController.Status s = controller.status();
        assertEquals("DISABLED", s.eZee());
        assertFalse(s.enabled());
        verifyNoInteractions(GATEWAY);
        verify(LIMITER, never()).acquire(any());
    }

    @Test
    void secondCallWithinTtlIsServedFromCache() {
        as("ROLE_SUPERADMIN");
        controller.status();
        controller.status();
        verify(GATEWAY, times(1)).json(eq(EzeeFamily.KIOSK), eq("RetrieveCurrency"), eq(Map.of()),
                eq(PmsPriority.HUB));
    }

    @Test
    void failedRefreshAfterGoodProbeReportsUpstreamStaleWithOldCheckedAt() {
        as("ROLE_SUPERADMIN");
        PROPS.setTtl(Map.of("status", 0L));
        long good = controller.status().checkedAt();
        when(GATEWAY.json(any(), any(), any(), any())).thenThrow(new PmsAuthException("bad"));
        PmsStatusController.Status s = controller.status();
        assertEquals("UPSTREAM", s.eZee());
        assertTrue(s.stale());
        assertEquals(good, s.checkedAt());
    }

    @Test
    void configuredFeaturesIsSortedUnionOfWritesAndCanary() {
        PmsProperties p = new PmsProperties();
        p.setWrites(Map.of("b", true));
        p.setCanary(Map.of("a", List.of("x"), "b", List.of("y")));
        assertEquals(List.of("a", "b"), List.copyOf(new PmsFlags(p).configuredFeatures()));
        assertTrue(new PmsFlags(new PmsProperties()).configuredFeatures().isEmpty());
    }
}

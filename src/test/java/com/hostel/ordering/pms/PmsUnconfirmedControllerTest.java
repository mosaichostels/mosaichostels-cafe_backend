package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.pms.PmsUnconfirmedController.ResolveRequest;
import com.hostel.ordering.pms.PmsUnconfirmedController.UnconfirmedClaim;
import com.hostel.ordering.pms.PmsWriteClaim.State;
import com.hostel.ordering.service.AuditService;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Runs the controller through real method security with the real role hierarchy. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PmsUnconfirmedControllerTest.Cfg.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PmsUnconfirmedControllerTest {

    static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");
    // not a bean: Spring would try to autowire the mock's inherited @Autowired field
    static final AuditService AUDIT = mock(AuditService.class);

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        InMemoryPmsWriteClaimStore store() {
            return new InMemoryPmsWriteClaimStore();
        }

        @Bean
        PmsWriteGuard guard(InMemoryPmsWriteClaimStore store) {
            return new PmsWriteGuard(new PmsFlags(new PmsProperties()), store, AUDIT, new ObjectMapper(),
                    Clock.fixed(T0, ZoneOffset.UTC));
        }

        @Bean
        PmsUnconfirmedController controller(PmsWriteGuard guard) {
            return new PmsUnconfirmedController(guard);
        }
    }

    @Autowired
    PmsUnconfirmedController controller;

    @Autowired
    InMemoryPmsWriteClaimStore store;

    private final AuditService audit = AUDIT;

    @BeforeEach
    void seed() {
        reset(AUDIT);
        PmsWriteClaim c = new PmsWriteClaim("k1", "room-status", "R101", "hash", "ROOM_STATUS", T0);
        c.setState(State.UNCONFIRMED);
        c.setOpenLockKey("room-status:R101");
        c.setResultJson("{\"guest\":\"secret\"}");
        store.insert(c);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
    }

    @Test
    void bothEndpointsAreAdminGated() throws Exception {
        assertEquals("hasRole('ADMIN')",
                PmsUnconfirmedController.class.getMethod("list").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('ADMIN')", PmsUnconfirmedController.class
                .getMethod("resolve", String.class, ResolveRequest.class).getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void staffGets403OnBothEndpoints() {
        as("ROLE_STAFF");
        assertThrows(AccessDeniedException.class, controller::list);
        assertThrows(AccessDeniedException.class, () -> controller.resolve("k1", new ResolveRequest("APPLIED")));
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
    }

    @Test
    void unauthenticatedIsRefused() {
        // over HTTP the filter chain answers 401 first (SecurityConfig: anyRequest().authenticated())
        assertThrows(AuthenticationCredentialsNotFoundException.class, controller::list);
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.resolve("k1", new ResolveRequest("APPLIED")));
    }

    @Test
    void adminAndSuperAdminCanList() {
        as("ROLE_SUPERADMIN");
        assertDoesNotThrow(controller::list);
        as("ROLE_ADMIN");
        List<UnconfirmedClaim> open = controller.list();
        assertEquals(List.of(new UnconfirmedClaim("k1", "room-status", "R101", T0.toEpochMilli())), open);
    }

    @Test
    void listCarriesIdsOnlyAndNoPayloads() {
        List<String> fields = Arrays.stream(UnconfirmedClaim.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(List.of("idempotencyKey", "feature", "targetId", "createdAt"), fields);
    }

    @Test
    void unknownOrMissingOutcomeIs400() {
        as("ROLE_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> controller.resolve("k1", new ResolveRequest("MAYBE")));
        assertThrows(IllegalArgumentException.class, () -> controller.resolve("k1", new ResolveRequest(null)));
        assertThrows(IllegalArgumentException.class, () -> controller.resolve("k1", null));
        assertEquals(State.UNCONFIRMED, store.find("k1").getState());
    }

    @Test
    void unknownKeyIs404() {
        as("ROLE_ADMIN");
        assertEquals(HttpStatus.NOT_FOUND,
                controller.resolve("nope", new ResolveRequest("APPLIED")).getStatusCode());
    }

    @Test
    void appliedMarksDoneAndAuditsIdsOnly() {
        as("ROLE_ADMIN");
        assertEquals(HttpStatus.NO_CONTENT, controller.resolve("k1", new ResolveRequest("APPLIED")).getStatusCode());
        assertEquals(State.DONE, store.find("k1").getState());
        assertNull(store.find("k1").getOpenLockKey());
        verify(audit).logAction("PMS_ROOM_STATUS_RESOLVED", "idempotencyKey=k1 targetId=R101 outcome=APPLIED");
        // already resolved: nothing open under that key
        assertEquals(HttpStatus.NOT_FOUND,
                controller.resolve("k1", new ResolveRequest("APPLIED")).getStatusCode());
    }

    @Test
    void notAppliedDeletesTheClaim() {
        as("ROLE_ADMIN");
        assertEquals(HttpStatus.NO_CONTENT,
                controller.resolve("k1", new ResolveRequest("NOT_APPLIED")).getStatusCode());
        assertNull(store.find("k1"));
        verify(audit).logAction("PMS_ROOM_STATUS_RESOLVED", "idempotencyKey=k1 targetId=R101 outcome=NOT_APPLIED");
    }
}

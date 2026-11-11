package com.hostel.ordering.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.controller.NotificationController;
import com.hostel.ordering.controller.OrderController;
import com.hostel.ordering.pms.PmsBookingService;
import com.hostel.ordering.pms.PmsFrontDeskController;
import com.hostel.ordering.pms.PmsFrontDeskService;
import com.hostel.ordering.pms.PmsGuestService;
import com.hostel.ordering.pms.PmsHousekeepingController;
import com.hostel.ordering.pms.PmsHousekeepingService;
import com.hostel.ordering.pms.PmsHousekeepingWorkflowController;
import com.hostel.ordering.pms.PmsHousekeepingWorkflowService;
import com.hostel.ordering.pms.PmsRoomStatusWriteService;
import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.ConfirmTokenService;
import com.hostel.ordering.service.FCMNotificationService;
import com.hostel.ordering.service.OrderService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Role by route matrix for the staff-facing endpoints. A route is "allowed" when the method security check does not refuse it. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RoleMatrixTest.Cfg.class)
class RoleMatrixTest {

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        OrderController orders() {
            return new OrderController(mock(OrderService.class), mock(AuditService.class), mock(ConfirmTokenService.class));
        }

        @Bean
        NotificationController notifications() {
            return new NotificationController(mock(FCMNotificationService.class));
        }

        @Bean
        PmsFrontDeskController frontDesk() {
            return new PmsFrontDeskController(mock(PmsFrontDeskService.class), mock(PmsBookingService.class),
                    mock(PmsGuestService.class));
        }

        @Bean
        PmsHousekeepingController housekeeping() {
            return new PmsHousekeepingController(mock(PmsHousekeepingService.class), mock(PmsRoomStatusWriteService.class));
        }

        @Bean
        PmsHousekeepingWorkflowController cleaning() {
            return new PmsHousekeepingWorkflowController(mock(PmsHousekeepingWorkflowService.class));
        }
    }

    @Autowired OrderController orders;
    @Autowired NotificationController notifications;
    @Autowired PmsFrontDeskController frontDesk;
    @Autowired PmsHousekeepingController housekeeping;
    @Autowired PmsHousekeepingWorkflowController cleaning;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static boolean allowed(Runnable call) {
        try {
            call.run();
            return true;
        } catch (AccessDeniedException e) {
            return false;
        } catch (RuntimeException e) {
            return true; // passed the security check, then hit the mocked service
        }
    }

    private Map<String, Runnable> routes() {
        Map<String, Runnable> m = new LinkedHashMap<>();
        m.put("GET /orders", () -> orders.getAllOrders(null, null, null, null, null, null, "createdAt_desc"));
        m.put("GET /orders/{id}", () -> orders.getOrder("x"));
        m.put("PUT /orders/{id}/status", () -> orders.updateOrderStatus("x", Map.of("status", "DELIVERED"), null, null));
        m.put("POST /notifications/subscribe", () -> notifications.subscribe(Map.of("token", "t")));
        m.put("POST /notifications/unsubscribe", () -> notifications.unsubscribe(Map.of("token", "t")));
        m.put("GET /pms/cleaning-tasks", () -> cleaning.tasks(null));
        m.put("POST /pms/cleaning-tasks/{k}/assign", () -> cleaning.assign("k", "k", null));
        m.put("PUT /pms/cleaning-assignments/{id}", () -> cleaning.update("id", "k", null));
        m.put("GET /pms/housekeeping/rooms", () -> housekeeping.rooms());
        m.put("POST /pms/housekeeping/rooms/{id}/status", () -> housekeeping.setStatus("r", "k", null));
        m.put("GET /pms/arrivals", () -> frontDesk.arrivals(null, null));
        m.put("GET /pms/inhouse", () -> frontDesk.inhouse());
        m.put("GET /pms/guests", () -> frontDesk.guests("abc"));
        return m;
    }

    private void expect(String role, String... allowedRoutes) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
        java.util.Set<String> ok = java.util.Set.of(allowedRoutes);
        routes().forEach((name, call) -> assertEquals(ok.contains(name), allowed(call), role + " " + name));
    }

    private static final String[] ORDER_AND_PUSH = {"GET /orders", "GET /orders/{id}", "PUT /orders/{id}/status",
            "POST /notifications/subscribe", "POST /notifications/unsubscribe"};
    private static final String[] CLEANING = {"GET /pms/cleaning-tasks", "POST /pms/cleaning-tasks/{k}/assign",
            "PUT /pms/cleaning-assignments/{id}", "GET /pms/housekeeping/rooms", "POST /pms/housekeeping/rooms/{id}/status"};
    private static final String[] FRONT_DESK = {"GET /pms/arrivals", "GET /pms/inhouse", "GET /pms/guests"};

    private static String[] concat(String[]... parts) {
        return java.util.Arrays.stream(parts).flatMap(java.util.Arrays::stream).toArray(String[]::new);
    }

    @Test
    void kitchenGetsOrdersAndPushOnly() {
        expect("ROLE_KITCHEN", ORDER_AND_PUSH);
    }

    @Test
    void housekeepingGetsCleaningAndBoardOnly() {
        expect("ROLE_HOUSEKEEPING", CLEANING);
    }

    @Test
    void adminAndSuperAdminGetEverything() {
        expect("ROLE_ADMIN", concat(ORDER_AND_PUSH, CLEANING, FRONT_DESK));
        expect("ROLE_SUPERADMIN", concat(ORDER_AND_PUSH, CLEANING, FRONT_DESK));
    }

    @Test
    void staffKeepsOrdersAndPushButHasNoPms() {
        expect("ROLE_STAFF", ORDER_AND_PUSH);
    }
}

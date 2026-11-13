package com.hostel.ordering.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.controller.CleaningListController;
import com.hostel.ordering.controller.NotificationController;
import com.hostel.ordering.controller.ScheduleController;
import com.hostel.ordering.service.ScheduleService;
import com.hostel.ordering.controller.OrderController;
import com.hostel.ordering.dto.CreateOrderRequest;
import com.hostel.ordering.pms.PmsHousekeepingController;
import com.hostel.ordering.pms.PmsHousekeepingService;
import com.hostel.ordering.pms.PmsHousekeepingWorkflowController;
import com.hostel.ordering.pms.PmsHousekeepingWorkflowService;
import com.hostel.ordering.pms.PmsReconcileController;
import com.hostel.ordering.pms.PmsReconcileService;
import com.hostel.ordering.pms.PmsRoomStatusWriteService;
import com.hostel.ordering.pms.PmsUnconfirmedController;
import com.hostel.ordering.pms.PmsWriteGuard;
import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.CleaningListService;
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
        PmsHousekeepingController housekeeping() {
            PmsHousekeepingService svc = mock(PmsHousekeepingService.class);
            org.mockito.Mockito.when(svc.rooms()).thenReturn(new com.hostel.ordering.pms.Cached<>(java.util.List.of(), 0L, false));
            return new PmsHousekeepingController(svc, mock(PmsRoomStatusWriteService.class));
        }

        @Bean
        CleaningListController cleaningList() {
            CleaningListService svc = mock(CleaningListService.class);
            org.mockito.Mockito.when(svc.done(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new CleaningListService.DoneResult(true, null, null));
            org.mockito.Mockito.when(svc.submit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new CleaningListService.SubmitResult(0, 0));
            org.mockito.Mockito.when(svc.remove(org.mockito.ArgumentMatchers.any())).thenReturn(true);
            return new CleaningListController(svc, mock(AuditService.class));
        }

        @Bean
        ScheduleController schedule() {
            ScheduleService svc = mock(ScheduleService.class);
            org.mockito.Mockito.when(svc.tick(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new ScheduleService.TickResult(false, "u"));
            org.mockito.Mockito.when(svc.confirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
            return new ScheduleController(svc, mock(AuditService.class), mock(com.hostel.ordering.service.CleaningNotifier.class));
        }

        @Bean
        PmsReconcileController reconcile() {
            return new PmsReconcileController(mock(PmsReconcileService.class));
        }

        @Bean
        PmsUnconfirmedController unconfirmed() {
            return new PmsUnconfirmedController(mock(PmsWriteGuard.class));
        }

        @Bean
        PmsHousekeepingWorkflowController cleaning() {
            return new PmsHousekeepingWorkflowController(mock(PmsHousekeepingWorkflowService.class));
        }
    }

    @Autowired OrderController orders;
    @Autowired NotificationController notifications;
    @Autowired PmsHousekeepingController housekeeping;
    @Autowired PmsHousekeepingWorkflowController cleaning;
    @Autowired CleaningListController cleaningList;
    @Autowired ScheduleController schedule;
    @Autowired PmsReconcileController reconcile;
    @Autowired PmsUnconfirmedController unconfirmed;

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
        }
    }

    private Map<String, Runnable> routes() {
        Map<String, Runnable> m = new LinkedHashMap<>();
        m.put("POST /orders", () -> orders.createOrder(new CreateOrderRequest(), null, null));
        m.put("GET /orders", () -> orders.getAllOrders(null, null, null, null, null, null, "createdAt_desc"));
        m.put("GET /orders/{id}", () -> orders.getOrder("x"));
        m.put("PUT /orders/{id}/status", () -> orders.updateOrderStatus("x", Map.of("status", "DELIVERED"), null, null));
        m.put("POST /notifications/subscribe", () -> notifications.subscribe(Map.of("token", "t")));
        m.put("POST /notifications/unsubscribe", () -> notifications.unsubscribe(Map.of("token", "t")));
        m.put("GET /pms/cleaning-tasks", () -> cleaning.tasks(null));
        m.put("GET /pms/housekeeping/rooms", () -> housekeeping.rooms());
        m.put("POST /pms/housekeeping/rooms/{id}/status", () -> housekeeping.setStatus("r", "k", null));
        var auth = new TestingAuthenticationToken("u", "p");
        m.put("GET /cleaning-list", () -> cleaningList.list());
        m.put("POST /cleaning-list", () -> cleaningList.submit(Map.of("beds", java.util.List.of()), auth));
        m.put("DELETE /cleaning-list/{id}", () -> cleaningList.remove("x", auth));
        m.put("POST /cleaning-list/{id}/done", () -> cleaningList.done("x", auth));
        m.put("GET /schedule/days", () -> schedule.days("2026-11-01", "2026-11-13"));
        m.put("POST /schedule/days/{date}/ticks", () -> schedule.tick("2026-11-13", Map.of("taskKey", "k", "slot", "ONCE"), auth));
        m.put("POST /schedule/.../confirm", () -> schedule.confirm("2026-11-13", "k", "ONCE", auth));
        m.put("GET /pms/reconcile/charges", () -> reconcile.charges(null));
        m.put("GET /pms/unconfirmed", () -> unconfirmed.list());
        return m;
    }

    private void expect(String role, String... allowedRoutes) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
        java.util.Set<String> ok = new java.util.HashSet<>(java.util.Arrays.asList(allowedRoutes));
        ok.add("POST /orders"); // guest ordering is open to every role
        routes().forEach((name, call) -> assertEquals(ok.contains(name), allowed(call), role + " " + name));
    }

    private static final String[] ORDER_AND_PUSH = {"GET /orders", "GET /orders/{id}", "PUT /orders/{id}/status",
            "POST /notifications/subscribe", "POST /notifications/unsubscribe"};
    private static final String[] CLEANING = {"GET /pms/cleaning-tasks", "GET /pms/housekeeping/rooms",
            "POST /pms/housekeeping/rooms/{id}/status"};
    private static final String[] LIST_HK = {"GET /cleaning-list", "POST /cleaning-list/{id}/done"};
    private static final String[] LIST_ADMIN = {"POST /cleaning-list", "DELETE /cleaning-list/{id}"};
    private static final String[] SCHED_HK = {"GET /schedule/days", "POST /schedule/days/{date}/ticks"};
    private static final String[] SCHED_ADMIN = {"POST /schedule/.../confirm"};
    private static final String[] ADMIN_ONLY = {"GET /pms/reconcile/charges", "GET /pms/unconfirmed"};

    private static String[] concat(String[]... parts) {
        return java.util.Arrays.stream(parts).flatMap(java.util.Arrays::stream).toArray(String[]::new);
    }

    @Test
    void kitchenGetsOrdersAndPushOnly() {
        expect("ROLE_KITCHEN", ORDER_AND_PUSH);
    }

    @Test
    void housekeepingGetsCleaningAndBoardOnly() {
        expect("ROLE_HOUSEKEEPING", concat(CLEANING, LIST_HK, SCHED_HK));
    }

    @Test
    void adminAndSuperAdminGetEverything() {
        expect("ROLE_ADMIN", concat(ORDER_AND_PUSH, CLEANING, LIST_HK, LIST_ADMIN, SCHED_HK, SCHED_ADMIN, ADMIN_ONLY));
        expect("ROLE_SUPERADMIN", concat(ORDER_AND_PUSH, CLEANING, LIST_HK, LIST_ADMIN, SCHED_HK, SCHED_ADMIN, ADMIN_ONLY));
    }

    @Test
    void legacyStaffRoleKeepsOrdersAndPushButHasNoPms() {
        expect("ROLE_STAFF", ORDER_AND_PUSH);
    }

    @Test
    void guestsCanStillPlaceOrdersWithoutLoggingIn() {
        SecurityContextHolder.clearContext();
        assertEquals(true, allowedWithoutLogin(() -> orders.createOrder(new CreateOrderRequest(), null, null)));
    }

    private static boolean allowedWithoutLogin(Runnable call) {
        try {
            call.run();
            return true;
        } catch (AccessDeniedException | org.springframework.security.authentication.AuthenticationCredentialsNotFoundException e) {
            return false;
        }
    }
}

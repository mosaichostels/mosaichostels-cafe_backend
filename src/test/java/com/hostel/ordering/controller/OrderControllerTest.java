package com.hostel.ordering.controller;

import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    OrderService orderService;

    @Mock
    AuditService auditService;

    @Mock
    Authentication authentication;

    private OrderController createController() {
        return new OrderController(orderService, auditService);
    }

    @Test
    void getAuthenticatedUser_nullAuthentication_returnsGuest() {
        OrderController controller = createController();

        String result = controller.getAuthenticatedUser(null);

        assertEquals("GUEST", result);
    }

    @Test
    void getAuthenticatedUser_notAuthenticated_returnsGuest() {
        when(authentication.isAuthenticated()).thenReturn(false);

        OrderController controller = createController();
        String result = controller.getAuthenticatedUser(authentication);

        assertEquals("GUEST", result);
    }

    @Test
    void getAuthenticatedUser_authenticatedWithAnonymousUser_returnsGuest() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("anonymousUser");

        OrderController controller = createController();
        String result = controller.getAuthenticatedUser(authentication);

        assertEquals("GUEST", result);
    }

    @Test
    void getAuthenticatedUser_authenticatedWithUsername_returnsUsername() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("naveen");

        OrderController controller = createController();
        String result = controller.getAuthenticatedUser(authentication);

        assertEquals("naveen", result);
    }

    private static String preAuthorizeOf(String methodName) throws Exception {
        for (Method m : OrderController.class.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
                return annotation == null ? null : annotation.value();
            }
        }
        throw new AssertionError("No such method: " + methodName);
    }

    @Test
    void updateOrderStatus_isAllowedForStaffAndAdmin() throws Exception {
        String rule = preAuthorizeOf("updateOrderStatus");

        assertTrue(rule.contains("STAFF"), "Staff must be able to mark orders delivered: " + rule);
        assertTrue(rule.contains("ADMIN"), "Admin must keep the permission: " + rule);
    }

    @Test
    void destructiveAndBillingEndpoints_stayAdminOnly() throws Exception {
        assertEquals("hasRole('ADMIN')", preAuthorizeOf("deleteOrder"));
        assertEquals("hasRole('ADMIN')", preAuthorizeOf("deleteOrders"));
        assertEquals("hasRole('ADMIN')", preAuthorizeOf("postCharge"));
    }
}

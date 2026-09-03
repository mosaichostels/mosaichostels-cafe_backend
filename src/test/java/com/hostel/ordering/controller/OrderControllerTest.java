package com.hostel.ordering.controller;

import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

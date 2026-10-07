package com.hostel.ordering.controller;

import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.ConfirmTokenService;
import com.hostel.ordering.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerDeleteTest {

    @Mock OrderService orderService;
    @Mock AuditService auditService;
    @Mock Authentication authentication;

    ConfirmTokenService tokens = new ConfirmTokenService();
    OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, auditService, tokens);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("admin");
    }

    private ResponseEntity<?> delete(String status, boolean all, String token) {
        return controller.deleteOrders(status, null, null, null, null, null, all, token, null, authentication);
    }

    @Test
    void blankFilterIsNotAFilter() {
        assertThrows(IllegalArgumentException.class, () -> delete("", false, null));
        assertThrows(IllegalArgumentException.class, () -> delete("   ", false, null));
        verifyNoInteractions(orderService);
    }

    @Test
    void allCannotBeCombinedWithAFilter() {
        assertThrows(IllegalArgumentException.class, () -> delete("DELIVERED", true, null));
        verifyNoInteractions(orderService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteAllWithoutToken_issuesServerSideToken_andDeletesNothing() {
        ResponseEntity<?> res = delete(null, true, null);

        assertEquals(400, res.getStatusCode().value());
        assertNotNull(((Map<String, Object>) res.getBody()).get("confirmToken"));
        verifyNoInteractions(orderService);
    }

    @Test
    void deleteAllRejectsAMadeUpTimestampToken() {
        assertThrows(IllegalArgumentException.class,
                () -> delete(null, true, String.valueOf(System.currentTimeMillis())));
        verifyNoInteractions(orderService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteAllWithIssuedToken_runsOnce() {
        when(orderService.deleteAllOrders()).thenReturn(3);
        String token = (String) ((Map<String, Object>) delete(null, true, null).getBody()).get("confirmToken");

        assertEquals(200, delete(null, true, token).getStatusCode().value());
        verify(orderService).deleteAllOrders();
        assertThrows(IllegalArgumentException.class, () -> delete(null, true, token));
    }
}

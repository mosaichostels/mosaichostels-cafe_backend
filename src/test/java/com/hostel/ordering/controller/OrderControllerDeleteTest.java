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

    @Test
    @SuppressWarnings("unchecked")
    void broadFilteredDeleteNeedsTheSameServerToken() {
        // dateFrom=0 matches every order: a filter in name only
        ResponseEntity<?> first = controller.deleteOrders(null, null, null, 0L, null, null, false, null, null, authentication);

        assertEquals(400, first.getStatusCode().value());
        String token = (String) ((Map<String, Object>) first.getBody()).get("confirmToken");
        assertNotNull(token);
        verifyNoInteractions(orderService);

        when(orderService.deleteFilteredOrders(null, null, null, 0L, null, null)).thenReturn(5);
        assertEquals(200, controller.deleteOrders(null, null, null, 0L, null, null, false, token, null, authentication)
                .getStatusCode().value());
        verify(orderService).deleteFilteredOrders(null, null, null, 0L, null, null);
    }

    @Test
    void filteredDeleteRejectsAMadeUpToken() {
        assertThrows(IllegalArgumentException.class, () -> controller.deleteOrders(
                "DELIVERED", null, null, null, null, null, false, "12345", null, authentication));
        verifyNoInteractions(orderService);
    }

    @Test
    void chargepost_passesTheAcknowledgementFlagToTheService() {
        when(orderService.postChargeForOrder("o1", "106", "admin", true)).thenReturn(new com.hostel.ordering.model.Order());

        controller.postCharge("o1", Map.of("room", "106", "acknowledgeUnconfirmed", "true"), null, authentication);

        verify(orderService).postChargeForOrder("o1", "106", "admin", true);
    }

    @Test
    void idempotencyKeysAreScopedPerOperation() {
        // The same client key sent to two different operations must not replay one's result in the other
        controller.deleteOrder("o1", "same-key");
        controller.deleteOrders("DELIVERED", null, null, null, null, null, false, null, "same-key", authentication);

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(orderService, atLeast(2)).getIdempotencyResult(keys.capture(), eq(String.class));
        assertEquals(2, keys.getAllValues().stream().distinct().count(), keys.getAllValues().toString());
    }

    @Test
    void createOrderPassesTheClientKeyToTheService() {
        when(orderService.createOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("k1"))).thenReturn(new com.hostel.ordering.model.Order());

        assertEquals(201, controller.createOrder(new com.hostel.ordering.dto.CreateOrderRequest(), "k1", authentication)
                .getStatusCode().value());
        verify(orderService).createOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("k1"));
    }
}

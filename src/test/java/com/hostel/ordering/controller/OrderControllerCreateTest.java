package com.hostel.ordering.controller;

import com.hostel.ordering.dto.CreateOrderRequest;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.service.AuditService;
import com.hostel.ordering.service.ConfirmTokenService;
import com.hostel.ordering.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OrderControllerCreateTest {

    OrderService orderService = mock(OrderService.class);
    OrderController controller;
    CreateOrderRequest request = new CreateOrderRequest();

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, mock(AuditService.class), new ConfirmTokenService());
    }

    @Test
    void aKeyAlreadyInFlight_isRefusedInsteadOfCreatingASecondOrder() {
        when(orderService.reserveIdempotencyKey("order-create:k1")).thenReturn(false);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.createOrder(request, "k1", null));

        assertEquals(409, e.getStatusCode().value());
        verify(orderService, never()).createOrder(any(), anyString());
    }

    @Test
    void aFreshKeyIsReservedThenResultIsCached() {
        Order created = new Order();
        when(orderService.reserveIdempotencyKey("order-create:k1")).thenReturn(true);
        when(orderService.createOrder(any(), anyString())).thenReturn(created);

        assertEquals(201, controller.createOrder(request, "k1", null).getStatusCode().value());
        verify(orderService).cacheIdempotencyResult("order-create:k1", created);
    }

    @Test
    void aFailedCreateReleasesTheKeySoTheRetryCanSucceed() {
        when(orderService.reserveIdempotencyKey("order-create:k1")).thenReturn(true);
        when(orderService.createOrder(any(), anyString())).thenThrow(new IllegalArgumentException("Unknown item"));

        assertThrows(IllegalArgumentException.class, () -> controller.createOrder(request, "k1", null));
        verify(orderService).releaseIdempotencyKey("order-create:k1");
    }

    @Test
    void noKeyMeansNoReservation() {
        when(orderService.createOrder(any(), anyString())).thenReturn(new Order());

        controller.createOrder(request, null, null);

        verify(orderService, never()).reserveIdempotencyKey(anyString());
    }
}

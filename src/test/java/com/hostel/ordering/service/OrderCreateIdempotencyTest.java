package com.hostel.ordering.service;

import com.hostel.ordering.dto.CreateOrderRequest;
import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The key lives on the saved order behind a unique index, so the database - not a separate
 * reservation record - decides which of two same-key requests creates the order.
 */
class OrderCreateIdempotencyTest {

    OrderRepository orderRepository = mock(OrderRepository.class);
    FCMNotificationService fcm = mock(FCMNotificationService.class);
    AuditService audit = mock(AuditService.class);
    DormitoryService dormitories = mock(DormitoryService.class);
    OrderService service;
    CreateOrderRequest request = new CreateOrderRequest();

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, fcm, audit, null, null, null, null, null, null, null, dormitories);
        when(dormitories.getAllDormitories()).thenReturn(List.of(new Dormitory("Dorm")));
        request.setBookingName("Joy");
        request.setDormitory("Dorm");
        request.setItems(new ArrayList<>());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void theKeyIsStoredOnTheSavedOrder_behindAUniqueSparseIndex() throws Exception {
        service.createOrder(request, "GUEST", "k1");

        org.mockito.ArgumentCaptor<Order> saved = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertEquals("k1", saved.getValue().getIdempotencyKey());

        Indexed idx = Order.class.getDeclaredField("idempotencyKey").getAnnotation(Indexed.class);
        assertTrue(idx != null && idx.unique() && idx.sparse(), "needs @Indexed(unique = true, sparse = true)");
    }

    @Test
    void aRetryOfAnOrderThatAlreadyExistsReturnsItWithoutSavingAnother() {
        Order existing = new Order();
        existing.setId("o1");
        when(orderRepository.findByIdempotencyKey("k1")).thenReturn(Optional.of(existing));

        assertSame(existing, service.createOrder(request, "GUEST", "k1"));
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(fcm);
    }

    @Test
    void ifTwoRequestsRaceTheLoserGetsTheWinnersOrderAndNoSecondNotification() {
        Order winner = new Order();
        winner.setId("o1");
        when(orderRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty(), Optional.of(winner));
        when(orderRepository.save(any(Order.class))).thenThrow(new DuplicateKeyException("dup"));

        assertSame(winner, service.createOrder(request, "GUEST", "k1"));
        verifyNoInteractions(fcm);
    }

    @Test
    void aFailureAfterTheSaveDoesNotFailTheRequest_soNoRetryIsProvoked() {
        doThrow(new RuntimeException("audit down")).when(audit).logAction(anyString(), anyString());

        Order result = service.createOrder(request, "GUEST", "k1");

        assertEquals("k1", result.getIdempotencyKey());
    }

    @Test
    void noKeyMeansNoLookupAndNothingStored() {
        Order result = service.createOrder(request, "GUEST", null);

        verify(orderRepository, never()).findByIdempotencyKey(anyString());
        assertNull(result.getIdempotencyKey());
    }
}

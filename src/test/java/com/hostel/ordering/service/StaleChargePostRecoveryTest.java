package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class StaleChargePostRecoveryTest {

    @Test
    void anOrderStuckInProgressBecomesUnconfirmedFailedInsteadOfDeadEnd() {
        OrderRepository repo = mock(OrderRepository.class);
        AuditService audit = mock(AuditService.class);
        Order stuck = new Order();
        stuck.setId("o1");
        stuck.setChargePostStatus("IN_PROGRESS");
        stuck.setChargePostedItems(new ArrayList<>(List.of("a#0")));
        when(repo.findStaleChargePosts(anyLong())).thenReturn(List.of(stuck));
        OrderService svc = new OrderService(repo, null, audit, null, null, null, null, null, null, null, null);

        svc.recoverStaleChargePosts();

        assertEquals("FAILED", stuck.getChargePostStatus());
        assertTrue(stuck.getChargePostedItems().contains("a#0"));
        assertTrue(stuck.getChargePostedItems().stream().anyMatch(i -> i.startsWith("UNCONFIRMED:")));
        verify(repo).save(stuck);
        verify(audit).logAction(eq("ORDER_CHARGEPOST_INTERRUPTED"), contains("o1"));
    }
}

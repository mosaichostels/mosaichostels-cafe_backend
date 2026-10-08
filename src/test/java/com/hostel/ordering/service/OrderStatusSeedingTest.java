package com.hostel.ordering.service;

import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.repository.OrderStatusRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** mongodb-init.js is a manual script; on a fresh database every status change was "Invalid status". */
class OrderStatusSeedingTest {

    @Test
    void emptyCollectionIsSeededWithTheFourLockedDefaults() {
        OrderStatusRepository repo = mock(OrderStatusRepository.class);
        List<OrderStatusConfig> store = new ArrayList<>();
        when(repo.findAll()).thenAnswer(i -> new ArrayList<>(store));
        when(repo.saveAll(any())).thenAnswer(i -> {
            Iterable<OrderStatusConfig> in = i.getArgument(0);
            in.forEach(store::add);
            return store;
        });

        List<OrderStatusConfig> result = new OrderStatusService(repo, mock(AuditService.class)).getAllStatuses();

        assertEquals(List.of("ORDERED", "DELIVERED", "CANCELLED", "CHECKED"),
                result.stream().map(OrderStatusConfig::getValue).toList());
        assertTrue(result.stream().allMatch(OrderStatusConfig::isLocked));
    }

    @Test
    void existingStatusesAreLeftAlone() {
        OrderStatusRepository repo = mock(OrderStatusRepository.class);
        when(repo.findAll()).thenReturn(List.of(new OrderStatusConfig("ORDERED", "Ordered", "x", true)));

        new OrderStatusService(repo, mock(AuditService.class)).getAllStatuses();

        verify(repo, never()).saveAll(any());
    }
}

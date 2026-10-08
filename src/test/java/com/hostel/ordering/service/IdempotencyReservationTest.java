package com.hostel.ordering.service;

import com.hostel.ordering.model.IdempotencyRecord;
import com.hostel.ordering.repository.IdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdempotencyReservationTest {

    @Test
    void firstReservationWins_theNextOneIsRefused() {
        IdempotencyRepository repo = mock(IdempotencyRepository.class);
        when(repo.insert(any(IdempotencyRecord.class)))
                .thenAnswer(i -> i.getArgument(0))
                .thenThrow(new DuplicateKeyException("dup"));
        IdempotencyService svc = new IdempotencyService(repo);

        assertTrue(svc.reserve("k"));
        assertFalse(svc.reserve("k"));
    }

    @Test
    void anUnavailableStoreDoesNotBlockOrders() {
        IdempotencyRepository repo = mock(IdempotencyRepository.class);
        when(repo.insert(any(IdempotencyRecord.class))).thenThrow(new RuntimeException("mongo down"));

        assertTrue(new IdempotencyService(repo).reserve("k"));
    }

    @Test
    void releaseDeletesTheReservation() {
        IdempotencyRepository repo = mock(IdempotencyRepository.class);
        new IdempotencyService(repo).release("k");
        verify(repo).deleteById("k");
    }
}

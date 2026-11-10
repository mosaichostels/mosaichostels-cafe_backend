package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PmsRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final PmsRateLimiter limiter = new PmsRateLimiter(new PmsProperties(), now::get);

    private void advanceSeconds(long s) {
        now.addAndGet(s * 1_000_000_000L);
    }

    @Test
    void hubIsCappedAtHubPer5s() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire(PmsPriority.HUB));
        }
        assertFalse(limiter.tryAcquire(PmsPriority.HUB));
        assertEquals(3, limiter.usedInWindow());
    }

    @Test
    void cafeUsesReserveAfterHubIsFull() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(PmsPriority.HUB);
        }
        assertTrue(limiter.tryAcquire(PmsPriority.CAFE));
        assertTrue(limiter.tryAcquire(PmsPriority.CAFE));
        assertFalse(limiter.tryAcquire(PmsPriority.CAFE));
        assertEquals(5, limiter.usedInWindow());
    }

    @Test
    void windowSlidesAfterFiveSeconds() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(PmsPriority.CAFE);
        }
        assertFalse(limiter.tryAcquire(PmsPriority.CAFE));

        advanceSeconds(4);
        assertFalse(limiter.tryAcquire(PmsPriority.CAFE));
        advanceSeconds(1);

        assertEquals(0, limiter.usedInWindow());
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire(PmsPriority.HUB));
        }
    }

    @Test
    void cafeAcquireIsNotBlockedWhileReserveRemains() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(PmsPriority.HUB);
        }
        assertFalse(limiter.tryAcquire(PmsPriority.HUB));

        long start = System.nanoTime();
        assertDoesNotThrow(() -> limiter.acquire(PmsPriority.CAFE));
        assertDoesNotThrow(() -> limiter.acquire(PmsPriority.CAFE));
        assertTrue(System.nanoTime() - start < 500_000_000L, "café acquire must not wait");
    }

    @Test
    void acquireThrowsBusyWhenNoSlotFreesWithinWait() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(PmsPriority.HUB);
        }
        PmsBusyException e = assertThrows(PmsBusyException.class,
                () -> limiter.acquire(PmsPriority.HUB, Duration.ofMillis(60)));
        assertEquals("PMS_BUSY", e.code());
    }

    @Test
    void recordCafeCallAlwaysCountsEvenWhenWindowIsFull() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire(PmsPriority.HUB));
        }
        limiter.recordCafeCall();
        limiter.recordCafeCall();
        assertEquals(5, limiter.usedInWindow());
        assertDoesNotThrow(limiter::recordCafeCall);
        assertEquals(6, limiter.usedInWindow());
    }

    @Test
    void hubWaitsWhileCafeCallsFillWindowThenFrees() {
        for (int i = 0; i < 4; i++) {
            limiter.recordCafeCall();
        }
        assertFalse(limiter.tryAcquire(PmsPriority.HUB));
        advanceSeconds(4);
        assertFalse(limiter.tryAcquire(PmsPriority.HUB));
        advanceSeconds(1);
        assertEquals(0, limiter.usedInWindow());
        assertTrue(limiter.tryAcquire(PmsPriority.HUB));
    }

    @Test
    void recordCafeCallEvictsExpiredEntriesOnTheAdvancingClock() {
        limiter.recordCafeCall();
        advanceSeconds(5);
        limiter.recordCafeCall();
        assertEquals(1, limiter.usedInWindow());
    }
}

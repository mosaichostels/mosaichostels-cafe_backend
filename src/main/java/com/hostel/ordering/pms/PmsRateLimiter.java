package com.hostel.ordering.pms;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sliding 5 s window shared by hub and café. eZee allows 5 requests per 5 s: the hub may fill
 * {@code hubPer5s} of them, the café may use all {@code hubPer5s + cafeReserve}.
 */
@Component
public class PmsRateLimiter {

    private static final long WINDOW_NANOS = Duration.ofSeconds(5).toNanos();
    private static final Duration MAX_WAIT = Duration.ofSeconds(2);
    private static final long POLL_MILLIS = 25;

    private final PmsProperties props;
    private final LongSupplier nanoClock;
    private final ArrayDeque<Long> acquired = new ArrayDeque<>();

    @Autowired
    public PmsRateLimiter(PmsProperties props) {
        this(props, System::nanoTime);
    }

    PmsRateLimiter(PmsProperties props, LongSupplier nanoClock) {
        this.props = props;
        this.nanoClock = nanoClock;
    }

    /** Takes a slot if one is free right now. */
    public synchronized boolean tryAcquire(PmsPriority priority) {
        evict();
        int limit = props.getHubPer5s() + (priority == PmsPriority.CAFE ? props.getCafeReserve() : 0);
        if (acquired.size() >= limit) {
            return false;
        }
        acquired.addLast(nanoClock.getAsLong());
        return true;
    }

    /** Waits up to 2 s for a slot, then gives up with {@link PmsBusyException}. */
    public void acquire(PmsPriority priority) {
        acquire(priority, MAX_WAIT);
    }

    // The deadline uses the real clock (not the injected one) so a stalled test clock cannot hang the wait.
    void acquire(PmsPriority priority, Duration maxWait) {
        long deadline = System.nanoTime() + maxWait.toNanos();
        while (!tryAcquire(priority)) {
            if (System.nanoTime() >= deadline) {
                throw new PmsBusyException("PMS rate limit: no request slot free");
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PmsBusyException("PMS rate limit: interrupted while waiting");
            }
        }
    }

    public synchronized int usedInWindow() {
        evict();
        return acquired.size();
    }

    private void evict() {
        long cutoff = nanoClock.getAsLong() - WINDOW_NANOS;
        while (!acquired.isEmpty() && acquired.peekFirst() <= cutoff) {
            acquired.removeFirst();
        }
    }
}

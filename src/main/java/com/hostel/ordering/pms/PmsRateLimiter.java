package com.hostel.ordering.pms;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sliding 5 s window shared by hub and café. eZee allows 5 requests per 5 s. The caps apply to
 * {@code acquire}/{@code tryAcquire} only: a HUB acquire is admitted while the window holds fewer than
 * {@code hubPer5s} entries, a CAFE acquire while it holds fewer than {@code hubPer5s + cafeReserve}.
 * The café's live charge path does not acquire: {@code recordCafeCall} records each café request without any
 * limit (Ruling 8), so the window can briefly exceed the cap and hub reads then wait. HUB is also refused for
 * {@code cafeQuietMs} after the last café call, so hub reads yield to an in-progress café burst (roomquery + one AddExtraCharge per item). Hub calls made just before a
 * burst still count against the window, so this reduces but does not eliminate the chance of exceeding eZee's limit.
 */
@Component
public class PmsRateLimiter {

    private static final long WINDOW_NANOS = Duration.ofSeconds(5).toNanos();
    private static final Duration MAX_WAIT = Duration.ofSeconds(2);
    private static final long POLL_MILLIS = 25;

    private final PmsProperties props;
    private final LongSupplier nanoClock;
    private final ArrayDeque<Long> acquired = new ArrayDeque<>();
    private final long cafeQuietNanos;
    private boolean cafeCallSeen;
    private long lastCafeCallNanos;

    @Autowired
    public PmsRateLimiter(PmsProperties props) {
        this(props, System::nanoTime);
    }

    PmsRateLimiter(PmsProperties props, LongSupplier nanoClock) {
        this.props = props;
        this.nanoClock = nanoClock;
        this.cafeQuietNanos = Duration.ofMillis(props.getCafeQuietMs()).toNanos();
    }

    /** Takes a slot if one is free right now. */
    public synchronized boolean tryAcquire(PmsPriority priority) {
        evict();
        // Strict "<": a café call exactly cafeQuietMs old no longer blocks the hub.
        if (priority == PmsPriority.HUB && cafeCallSeen
                && nanoClock.getAsLong() - lastCafeCallNanos < cafeQuietNanos) {
            return false;
        }
        int limit = props.getHubPer5s() + (priority == PmsPriority.CAFE ? props.getCafeReserve() : 0);
        if (acquired.size() >= limit) {
            return false;
        }
        acquired.addLast(nanoClock.getAsLong());
        return true;
    }

    /**
     * Counts a café call against the window without ever refusing or delaying it: a charge post
     * cannot be voided, so the café must not be turned away. Hub requests absorb the overshoot.
     */
    public synchronized void recordCafeCall() {
        evict();
        long now = nanoClock.getAsLong();
        acquired.addLast(now);
        cafeCallSeen = true;
        lastCafeCallNanos = now;
    }

    /** Waits up to 2 s for a slot, then gives up with {@link PmsBusyException}. */
    public void acquire(PmsPriority priority) {
        acquire(priority, MAX_WAIT);
    }

    // The deadline uses the real clock (not the injected one) so a stalled test clock cannot hang the wait.
    /** Same wait loop with a caller-chosen bound; the 5 s window itself is unchanged, so this only waits longer. */
    public void acquire(PmsPriority priority, Duration maxWait) {
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

package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PmsCacheTest {

    private static final Duration TTL = Duration.ofSeconds(10);
    private final AtomicLong now = new AtomicLong(1_000);
    private final PmsCache cache = new PmsCache(now::get);

    @Test
    void freshHitSkipsLoader() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        cache.getOrLoad("k", TTL, () -> "a" + calls.incrementAndGet());
        now.addAndGet(9_999);
        Cached<String> hit = cache.getOrLoad("k", TTL, () -> "a" + calls.incrementAndGet());
        assertEquals("a1", hit.value());
        assertFalse(hit.stale());
        assertEquals(1_000, hit.fetchedAtMs());
        assertEquals(1, calls.get());
    }

    @Test
    void loaderFailureWithPriorValueReturnsItStale() throws Exception {
        cache.getOrLoad("k", TTL, () -> "old");
        now.addAndGet(10_000);
        Cached<String> c = cache.getOrLoad("k", TTL, () -> { throw new IOException("down"); });
        assertEquals("old", c.value());
        assertTrue(c.stale());
        assertEquals(1_000, c.fetchedAtMs());
    }

    @Test
    void loaderFailureWithNoPriorValueRethrowsAndIsNotCached() throws Exception {
        assertThrows(IOException.class, () -> cache.getOrLoad("k", TTL, () -> { throw new IOException("down"); }));
        Cached<String> c = cache.getOrLoad("k", TTL, () -> "ok");
        assertEquals("ok", c.value());
        assertFalse(c.stale());
    }

    @Test
    void laterSuccessReplacesStaleValue() throws Exception {
        cache.getOrLoad("k", TTL, () -> "old");
        now.addAndGet(10_000);
        assertTrue(cache.getOrLoad("k", TTL, () -> { throw new IOException("down"); }).stale());
        now.addAndGet(5);
        Cached<String> c = cache.getOrLoad("k", TTL, () -> "new");
        assertEquals("new", c.value());
        assertFalse(c.stale());
        assertEquals(11_005, c.fetchedAtMs());
    }

    @Test
    void concurrentCallersOnExpiredKeyCallLoaderOnce() throws Exception {
        cache.getOrLoad("k", TTL, () -> "old");
        now.addAndGet(10_000);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        Callable<String> loader = () -> {
            calls.incrementAndGet();
            release.await();
            return "new";
        };
        List<Cached<String>> results = runConcurrently(loader, release);
        assertEquals(1, calls.get());
        for (Cached<String> r : results) {
            assertEquals("new", r.value());
            assertFalse(r.stale());
        }
    }

    @Test
    void concurrentCallersDuringFailedLoadCallLoaderOnceAndShareOutcome() throws Exception {
        cache.getOrLoad("k", TTL, () -> "old");
        now.addAndGet(10_000);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        Callable<String> loader = () -> {
            calls.incrementAndGet();
            release.await();
            throw new IOException("down");
        };
        List<Cached<String>> results = runConcurrently(loader, release);
        assertEquals(1, calls.get());
        for (Cached<String> r : results) {
            assertEquals("old", r.value());
            assertTrue(r.stale());
        }
    }

    @Test
    void concurrentCallersDuringFailedLoadWithNoPriorValueAllGetTheException() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<Cached<String>>> futures = new ArrayList<>();
            Callable<String> loader = () -> {
                calls.incrementAndGet();
                release.await();
                throw new IOException("down");
            };
            submitAndAwaitWaiting(pool, loader, futures, release);
            for (Future<Cached<String>> f : futures) {
                assertTrue(assertThrows(java.util.concurrent.ExecutionException.class, f::get).getCause() instanceof IOException);
            }
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void capsAtFiveHundredKeysEvictingOldest() throws Exception {
        for (int i = 0; i < 500; i++) {
            cache.getOrLoad("k" + i, TTL, () -> "v");
            now.incrementAndGet();
        }
        cache.getOrLoad("k500", TTL, () -> "v");
        AtomicInteger calls = new AtomicInteger();
        cache.getOrLoad("k0", TTL, () -> "reloaded" + calls.incrementAndGet());
        assertEquals(1, calls.get(), "oldest key was evicted");
        AtomicInteger calls2 = new AtomicInteger();
        cache.getOrLoad("k500", TTL, () -> "x" + calls2.incrementAndGet());
        assertEquals(0, calls2.get(), "newest key is retained");
    }

    @Test
    void interruptedLoaderWithPriorValueServesStaleAndKeepsInterruptFlag() throws Exception {
        cache.getOrLoad("k", TTL, () -> "old");
        now.addAndGet(10_000);
        try {
            Cached<String> c = cache.getOrLoad("k", TTL, () -> { throw new InterruptedException(); });
            assertTrue(c.stale());
            assertEquals("old", c.value());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // clear so it cannot leak into other tests
        }
    }

    @Test
    void evictForcesReload() throws Exception {
        cache.getOrLoad("k", TTL, () -> "a");
        cache.evict("k");
        assertEquals("b", cache.getOrLoad("k", TTL, () -> "b").value());
    }

    private List<Cached<String>> runConcurrently(Callable<String> loader, CountDownLatch release) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<Cached<String>>> futures = new ArrayList<>();
            submitAndAwaitWaiting(pool, loader, futures, release);
            List<Cached<String>> out = new ArrayList<>();
            for (Future<Cached<String>> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Starts 20 callers, waits until all 20 are parked (the loader plus 19 followers), then releases it. */
    private void submitAndAwaitWaiting(ExecutorService pool, Callable<String> loader,
            List<Future<Cached<String>>> futures, CountDownLatch release) {
        List<Thread> threads = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                threads.add(Thread.currentThread());
                return cache.getOrLoad("k", TTL, loader);
            }));
        }
        while (threads.size() < 20 || waitingCount(threads) < 20) {
            Thread.onSpinWait();
        }
        release.countDown();
    }

    private long waitingCount(List<Thread> threads) {
        synchronized (threads) {
            return threads.stream().filter(t -> t.getState() == Thread.State.WAITING).count();
        }
    }
}

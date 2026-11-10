package com.hostel.ordering.pms;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory only (it will hold guest data: never persist or log values). One load per key at a time;
 * callers arriving during it share its outcome. A failed load serves the last good value as stale.
 */
@Component
public class PmsCache {

    private static final Logger log = LoggerFactory.getLogger(PmsCache.class);
    private static final int MAX_KEYS = 500;

    private record Entry(Object value, long fetchedAtMs) {}

    private final LongSupplier clockMs;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Cached<?>>> inFlight = new ConcurrentHashMap<>();

    @Autowired
    public PmsCache() {
        this(System::currentTimeMillis);
    }

    PmsCache(LongSupplier clockMs) {
        this.clockMs = clockMs;
    }

    @SuppressWarnings("unchecked")
    public <T> Cached<T> getOrLoad(String key, Duration ttl, Callable<T> loader) throws Exception {
        Entry e = entries.get(key);
        if (e != null && clockMs.getAsLong() - e.fetchedAtMs() < ttl.toMillis()) {
            return new Cached<>((T) e.value(), e.fetchedAtMs(), false);
        }
        CompletableFuture<Cached<?>> mine = new CompletableFuture<>();
        CompletableFuture<Cached<?>> leader = inFlight.putIfAbsent(key, mine);
        if (leader != null) {
            try {
                return (Cached<T>) leader.get();
            } catch (ExecutionException ex) {
                throw ex.getCause() instanceof Exception cause ? cause : ex;
            }
        }
        try {
            Cached<T> result;
            try {
                T value = loader.call();
                long at = clockMs.getAsLong();
                put(key, new Entry(value, at));
                result = new Cached<>(value, at, false);
            } catch (Exception ex) {
                Entry prior = entries.get(key);
                if (prior == null) {
                    mine.completeExceptionally(ex);
                    throw ex;
                }
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.debug("pms cache: load failed for key {}, serving stale", key);
                result = new Cached<>((T) prior.value(), prior.fetchedAtMs(), true);
            }
            mine.complete(result);
            return result;
        } catch (Throwable t) {
            mine.completeExceptionally(t); // no-op if already completed; never leave followers hanging
            throw t;
        } finally {
            inFlight.remove(key);
        }
    }

    public void evict(String key) {
        entries.remove(key);
    }

    private void put(String key, Entry entry) {
        entries.put(key, entry);
        if (entries.size() > MAX_KEYS) {
            entries.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().fetchedAtMs(), b.getValue().fetchedAtMs()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey()));
        }
    }
}

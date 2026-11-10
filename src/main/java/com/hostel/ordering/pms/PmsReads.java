package com.hostel.ordering.pms;

import java.time.Duration;
import java.util.concurrent.Callable;

/** Runs a cached hub read so callers see only unchecked Pms exceptions (mapped to 5xx by the handler). */
final class PmsReads {

    private PmsReads() {}

    static <T> Cached<T> load(PmsCache cache, String key, Duration ttl, Callable<T> loader) {
        try {
            return cache.getOrLoad(key, ttl, loader);
        } catch (RuntimeException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PmsUpstreamException("PMS read interrupted");
        } catch (Exception e) {
            throw new PmsUpstreamException("PMS read failed");
        }
    }
}

package com.hostel.ordering.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {
    private static final long IDEMPOTENCY_TTL_MINUTES = 60;
    private static final int MAX_CACHE_SIZE = 10000;
    
    private static class CacheEntry {
        final Object result;
        final long timestamp;
        
        CacheEntry(Object result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(IDEMPOTENCY_TTL_MINUTES);
        }
    }
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> lruOrder = new ConcurrentLinkedQueue<>();
    
    public Object getIfPresent(String idempotencyKey) {
        CacheEntry entry = cache.get(idempotencyKey);
        if (entry != null && !entry.isExpired()) {
            return entry.result;
        } else if (entry != null) {
            cache.remove(idempotencyKey);
            lruOrder.remove(idempotencyKey);
        }
        return null;
    }
    
    public void put(String idempotencyKey, Object result) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            String oldest = lruOrder.poll();
            if (oldest != null) {
                cache.remove(oldest);
            }
        }
        cache.put(idempotencyKey, new CacheEntry(result));
        lruOrder.offer(idempotencyKey);
    }
    
    public boolean contains(String idempotencyKey) {
        return getIfPresent(idempotencyKey) != null;
    }
}

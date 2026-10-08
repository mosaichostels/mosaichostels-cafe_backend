package com.hostel.ordering.service;

import com.hostel.ordering.model.IdempotencyRecord;
import com.hostel.ordering.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Remembers the result of a mutating request so a retry carrying the same Idempotency-Key
 * returns the original outcome instead of performing the operation twice.
 *
 * <p>Backed by Mongo rather than an in-process map. The map did not survive a restart, and this
 * backend runs on a Space that sleeps and redeploys, so precisely the retry most likely to
 * happen - the one after a request died mid-flight - was the one that would be treated as a
 * first attempt and could duplicate an order or an eZee charge. Mongo expires the records via
 * a TTL index on createdAt.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository repository;

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    public Object getIfPresent(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        try {
            return repository.findById(idempotencyKey)
                    .map(IdempotencyRecord::getResult)
                    .orElse(null);
        } catch (Exception e) {
            // A lookup failure must not fail the request. Missing the cache means the caller
            // performs the operation, which is the same position they were in before.
            log.warn("Idempotency lookup failed for key {}: {}", idempotencyKey, e.getMessage());
            return null;
        }
    }

    public <T> T getIfPresent(String idempotencyKey, Class<T> type) {
        Object cached = getIfPresent(idempotencyKey);
        return type.isInstance(cached) ? type.cast(cached) : null;
    }

    public void put(String idempotencyKey, Object result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            repository.save(new IdempotencyRecord(idempotencyKey, result));
        } catch (Exception e) {
            // The operation itself already succeeded; failing to record it only costs
            // deduplication on a later retry.
            log.warn("Could not store idempotency result for key {}: {}", idempotencyKey, e.getMessage());
        }
    }

    /**
     * Claims a key before the work starts. The insert is atomic (the key is the document id), so of
     * two requests carrying one key exactly one proceeds; the other must not repeat the work. A
     * crash between reserving and caching therefore leaves the key spent, which costs the guest one
     * extra tap on a fresh key - far better than a second order.
     */
    public boolean reserve(String idempotencyKey) {
        try {
            repository.insert(new IdempotencyRecord(idempotencyKey, null));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        } catch (Exception e) {
            // Store unavailable: behave as before reservations existed rather than block orders.
            log.warn("Could not reserve idempotency key {}: {}", idempotencyKey, e.getMessage());
            return true;
        }
    }

    /** Frees a reservation whose work failed, so the client's retry is not mistaken for a duplicate. */
    public void release(String idempotencyKey) {
        try {
            repository.deleteById(idempotencyKey);
        } catch (Exception e) {
            log.warn("Could not release idempotency key {}: {}", idempotencyKey, e.getMessage());
        }
    }

    public boolean contains(String idempotencyKey) {
        return getIfPresent(idempotencyKey) != null;
    }
}

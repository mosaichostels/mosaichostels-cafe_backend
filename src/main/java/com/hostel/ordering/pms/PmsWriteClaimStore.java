package com.hostel.ordering.pms;

import com.hostel.ordering.pms.PmsWriteClaim.State;
import java.time.Instant;
import java.util.List;

/**
 * Storage for {@link PmsWriteClaim}. Every state change is one atomic conditional operation that returns the
 * row as it was before the change, or null when the condition did not match (nothing changed).
 */
public interface PmsWriteClaimStore {

    /** Creates the claim; throws {@code DuplicateKeyException} if the id or the open lock key is taken. */
    void insert(PmsWriteClaim claim);

    PmsWriteClaim find(String id);

    /** The claim currently holding this lock key, or null. */
    PmsWriteClaim findOpen(String lockKey);

    List<PmsWriteClaim> findByState(State state);

    /** {@code from -> DONE}: stores the result and doneAt and drops the open lock key. */
    PmsWriteClaim markDone(String id, State from, String resultJson, Instant doneAt);

    /** {@code IN_PROGRESS -> UNCONFIRMED}, keeping the lock; with createdBefore, only if created at or before it. */
    PmsWriteClaim markUnconfirmed(String id, Instant createdBefore);

    /** Deletes the claim only if it is in {@code state}. */
    PmsWriteClaim delete(String id, State state);
}

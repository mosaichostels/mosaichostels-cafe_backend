package com.hostel.ordering.pms;

import com.hostel.ordering.pms.PmsWriteClaim.State;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;

/**
 * Test stand-in for {@link MongoPmsWriteClaimStore} with the same rules the Mongo indexes enforce: a unique
 * {@code _id}, a sparse unique {@code openLockKey} (rows without one are not indexed), and conditional
 * transitions. Every method is synchronized, matching Mongo's single-document atomicity. Rows are copied in
 * and out so callers cannot mutate stored state.
 */
class InMemoryPmsWriteClaimStore implements PmsWriteClaimStore {

    private final Map<String, PmsWriteClaim> rows = new HashMap<>();

    @Override
    public synchronized void insert(PmsWriteClaim claim) {
        if (rows.containsKey(claim.getId())) {
            throw new DuplicateKeyException("duplicate _id");
        }
        if (claim.getOpenLockKey() != null && findOpen(claim.getOpenLockKey()) != null) {
            throw new DuplicateKeyException("duplicate openLockKey");
        }
        rows.put(claim.getId(), copy(claim));
    }

    @Override
    public synchronized PmsWriteClaim find(String id) {
        return copy(rows.get(id));
    }

    @Override
    public synchronized PmsWriteClaim findOpen(String lockKey) {
        return rows.values().stream().filter(c -> lockKey.equals(c.getOpenLockKey())).findFirst()
                .map(InMemoryPmsWriteClaimStore::copy).orElse(null);
    }

    @Override
    public synchronized List<PmsWriteClaim> findByState(State state) {
        List<PmsWriteClaim> out = new ArrayList<>();
        rows.values().stream().filter(c -> c.getState() == state).forEach(c -> out.add(copy(c)));
        return out;
    }

    @Override
    public synchronized PmsWriteClaim markDone(String id, State from, String resultJson, Instant doneAt) {
        PmsWriteClaim row = rows.get(id);
        if (row == null || row.getState() != from) {
            return null;
        }
        PmsWriteClaim before = copy(row);
        row.setState(State.DONE);
        if (resultJson != null) {
            row.setResultJson(resultJson);
        }
        row.setDoneAt(doneAt);
        row.setOpenLockKey(null);
        return before;
    }

    @Override
    public synchronized PmsWriteClaim markUnconfirmed(String id, Instant createdBefore) {
        PmsWriteClaim row = rows.get(id);
        if (row == null || row.getState() != State.IN_PROGRESS
                || (createdBefore != null && row.getCreatedAt().isAfter(createdBefore))) {
            return null;
        }
        PmsWriteClaim before = copy(row);
        row.setState(State.UNCONFIRMED);
        return before;
    }

    @Override
    public synchronized PmsWriteClaim delete(String id, State state) {
        PmsWriteClaim row = rows.get(id);
        if (row == null || row.getState() != state) {
            return null;
        }
        return rows.remove(id);
    }

    synchronized int size() {
        return rows.size();
    }

    private static PmsWriteClaim copy(PmsWriteClaim c) {
        if (c == null) {
            return null;
        }
        PmsWriteClaim o = new PmsWriteClaim(c.getId(), c.getFeature(), c.getTargetId(), c.getRequestHash(),
                c.getAuditAction(), c.getCreatedAt());
        o.setOpenLockKey(c.getOpenLockKey());
        o.setState(c.getState());
        o.setResultJson(c.getResultJson());
        o.setDoneAt(c.getDoneAt());
        return o;
    }
}

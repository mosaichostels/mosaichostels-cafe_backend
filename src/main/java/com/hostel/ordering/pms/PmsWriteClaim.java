package com.hostel.ordering.pms;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One guarded PMS write, keyed by the client's Idempotency-Key. Holds ids, a request hash and the stored
 * result only; never guest names, phones or free text.
 */
@Document("pms_write_claims")
public class PmsWriteClaim {

    public enum State { IN_PROGRESS, DONE, UNCONFIRMED }

    @Id
    private String id;

    private String feature;
    private String targetId;

    // feature:targetId while IN_PROGRESS or UNCONFIRMED, unset when DONE. Spring Data omits nulls, so the
    // sparse unique index allows at most one open claim per target (same pattern as Order.idempotencyKey).
    @Indexed(unique = true, sparse = true)
    private String openLockKey;

    private String requestHash;
    private String auditAction;
    private State state;
    private String resultJson;
    private Instant createdAt;

    // Only DONE rows carry doneAt, so UNCONFIRMED and IN_PROGRESS rows never expire.
    @Indexed(expireAfterSeconds = 86400)
    private Instant doneAt;

    public PmsWriteClaim() {
    }

    public PmsWriteClaim(String id, String feature, String targetId, String requestHash, String auditAction,
            Instant createdAt) {
        this.id = id;
        this.feature = feature;
        this.targetId = targetId;
        this.requestHash = requestHash;
        this.auditAction = auditAction;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getOpenLockKey() { return openLockKey; }
    public void setOpenLockKey(String openLockKey) { this.openLockKey = openLockKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getAuditAction() { return auditAction; }
    public void setAuditAction(String auditAction) { this.auditAction = auditAction; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getDoneAt() { return doneAt; }
    public void setDoneAt(Instant doneAt) { this.doneAt = doneAt; }
}

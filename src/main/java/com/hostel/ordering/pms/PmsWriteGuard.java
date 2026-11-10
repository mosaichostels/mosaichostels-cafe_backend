package com.hostel.ordering.pms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.pms.PmsWriteClaim.State;
import com.hostel.ordering.service.AuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Lets at most one write per target reach eZee, never repeats a write for the same Idempotency-Key, and
 * never retries an ambiguous outcome on its own. Writes change real guest records, so an outcome the
 * guard cannot prove is parked as UNCONFIRMED (target stays locked) until an admin resolves it.
 */
@Component
public class PmsWriteGuard {

    private static final Logger log = LoggerFactory.getLogger(PmsWriteGuard.class);

    /** An IN_PROGRESS claim older than this was left by a crash; the call may have reached eZee. */
    static final Duration STUCK_AFTER = Duration.ofMinutes(5);

    // Keys are stored as _id and written to the audit log, so they must be ids, not free text.
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final PmsFlags flags;
    private final PmsWriteClaimStore store;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public PmsWriteGuard(PmsFlags flags, PmsWriteClaimStore store, AuditService audit, ObjectMapper mapper) {
        this(flags, store, audit, mapper, Clock.systemUTC());
    }

    PmsWriteGuard(PmsFlags flags, PmsWriteClaimStore store, AuditService audit, ObjectMapper mapper, Clock clock) {
        this.flags = flags;
        this.store = store;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** SHA-256 hex of the parts; each part is length-prefixed so ("a","bc") and ("ab","c") differ. */
    public static String hash(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            String s = String.valueOf(part);
            sb.append(s.length()).append(':').append(s).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Runs {@code eZeeCall} at most once for this key and at most one at a time for this target. Audit
     * entries carry only {@code auditDetails} as given, so callers must pass ids and action names only.
     */
    public <T> PmsWriteResult<T> execute(String feature, String targetId, String idempotencyKey, String requestHash,
            String auditAction, String auditDetails, Class<T> type, Supplier<T> eZeeCall, Predicate<T> readBack) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        if (!KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("Idempotency-Key is invalid");
        }
        flags.requireWrite(feature);
        List<String> canary = flags.canary(feature);
        if (!canary.isEmpty() && !canary.contains(targetId)) {
            throw new PmsFeatureDisabledException("PMS write target is outside the canary");
        }

        PmsWriteClaim claim = new PmsWriteClaim(idempotencyKey, feature, targetId, requestHash, auditAction,
                clock.instant());
        claim.setOpenLockKey(feature + ":" + targetId);
        claim.setState(State.IN_PROGRESS);
        try {
            store.insert(claim);
        } catch (DuplicateKeyException e) {
            return replayOrRefuse(claim, type);
        }

        T value;
        try {
            value = eZeeCall.get();
        } catch (RuntimeException e) {
            if (definitelyNotApplied(e)) {
                // eZee definitely did not apply the write: free the target so the caller can try again.
                store.delete(idempotencyKey, State.IN_PROGRESS);
                throw e;
            }
            log.warn("PMS write {} outcome unknown: {}", idempotencyKey, e.getClass().getSimpleName());
            markUnconfirmed(idempotencyKey, auditAction, auditDetails);
            throw new PmsUnconfirmedException("PMS write outcome unknown");
        }

        if (!readBackMatches(readBack, value)) {
            markUnconfirmed(idempotencyKey, auditAction, auditDetails);
            return new PmsWriteResult<>(value, false);
        }
        if (store.markDone(idempotencyKey, State.IN_PROGRESS, toJson(value), clock.instant()) == null) {
            // Already marked stuck (call outlived STUCK_AFTER); the claim stays UNCONFIRMED for an admin.
            return new PmsWriteResult<>(value, false);
        }
        audit.logAction("PMS_" + auditAction, auditDetails);
        return new PmsWriteResult<>(value, true);
    }

    /** UNCONFIRMED claims for the admin list; first parks any claim stuck IN_PROGRESS by a crash. */
    public List<PmsWriteClaim> unconfirmed() {
        store.findByState(State.IN_PROGRESS).forEach(this::isStuck);
        return store.findByState(State.UNCONFIRMED);
    }

    /** Admin decision after checking eZee by hand; false when no UNCONFIRMED claim has this key. */
    public boolean resolve(String idempotencyKey, boolean applied) {
        PmsWriteClaim before = applied
                ? store.markDone(idempotencyKey, State.UNCONFIRMED, null, clock.instant())
                : store.delete(idempotencyKey, State.UNCONFIRMED);
        if (before == null) {
            return false;
        }
        audit.logAction("PMS_" + before.getFeature().toUpperCase(Locale.ROOT).replace('-', '_') + "_RESOLVED",
                "idempotencyKey=" + before.getId() + " targetId=" + before.getTargetId()
                        + " outcome=" + (applied ? "APPLIED" : "NOT_APPLIED"));
        return true;
    }

    private <T> PmsWriteResult<T> replayOrRefuse(PmsWriteClaim mine, Class<T> type) {
        PmsWriteClaim existing = store.find(mine.getId());
        if (existing == null) {
            // The open lock key is held by another key (or our key's row vanished since the insert).
            PmsWriteClaim holder = store.findOpen(mine.getOpenLockKey());
            if (holder != null) {
                isStuck(holder);
            }
            throw new PmsTargetBusyException("Another PMS write holds this target");
        }
        if (!Objects.equals(existing.getRequestHash(), mine.getRequestHash())
                || !Objects.equals(existing.getFeature(), mine.getFeature())
                || !Objects.equals(existing.getTargetId(), mine.getTargetId())) {
            throw new PmsIdempotencyMismatchException("Idempotency-Key reused for a different PMS write");
        }
        switch (existing.getState()) {
            case DONE:
                return new PmsWriteResult<>(fromJson(existing.getResultJson(), type), true);
            case IN_PROGRESS:
                if (!isStuck(existing)) {
                    throw new PmsInProgressException("PMS write already in progress");
                }
                // fall through: a stuck claim is an unknown outcome
            default:
                throw new PmsUnconfirmedException("PMS write outcome unknown");
        }
    }

    /** True if the claim is IN_PROGRESS past STUCK_AFTER; atomically parks it as UNCONFIRMED (once). */
    private boolean isStuck(PmsWriteClaim claim) {
        Instant cutoff = clock.instant().minus(STUCK_AFTER);
        if (claim.getState() != State.IN_PROGRESS || claim.getCreatedAt().isAfter(cutoff)) {
            return false;
        }
        if (store.markUnconfirmed(claim.getId(), cutoff) != null) {
            audit.logAction("PMS_" + claim.getAuditAction() + "_UNCONFIRMED",
                    "idempotencyKey=" + claim.getId() + " targetId=" + claim.getTargetId());
        }
        return true;
    }

    private void markUnconfirmed(String idempotencyKey, String auditAction, String auditDetails) {
        if (store.markUnconfirmed(idempotencyKey, null) != null) {
            audit.logAction("PMS_" + auditAction + "_UNCONFIRMED", auditDetails);
        }
    }

    /** Refusals before or by eZee, and gateway failures raised before any request left (not sent). */
    private static boolean definitelyNotApplied(RuntimeException e) {
        return e instanceof PmsBusyException || e instanceof PmsAuthException || e instanceof PmsNotEnabledException
                || e instanceof PmsFeatureDisabledException
                || (e instanceof PmsUpstreamException u && !u.isSent());
    }

    private static <T> boolean readBackMatches(Predicate<T> readBack, T value) {
        try {
            return readBack.test(value);
        } catch (RuntimeException e) {
            return false; // could not verify, so the outcome is not proven
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // The write is confirmed; a replay then returns no value rather than a wrong one.
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null) {
            return null; // resolved APPLIED by an admin, or a result that could not be stored
        }
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored PMS result is unreadable");
        }
    }
}

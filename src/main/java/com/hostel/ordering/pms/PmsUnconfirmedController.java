package com.hostel.ordering.pms;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin view of PMS writes whose outcome is unknown, and the hand-checked resolution of each. */
@RestController
@RequestMapping("/pms/unconfirmed")
public class PmsUnconfirmedController {

    /** Ids only: no request or result payloads. */
    public record UnconfirmedClaim(String idempotencyKey, String feature, String targetId, long createdAt) {}

    /** No free-text note: nothing typed here is stored or audited. */
    public record ResolveRequest(String outcome) {}

    private final PmsWriteGuard guard;

    public PmsUnconfirmedController(PmsWriteGuard guard) {
        this.guard = guard;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UnconfirmedClaim> list() {
        return guard.unconfirmed().stream()
                .map(c -> new UnconfirmedClaim(c.getId(), c.getFeature(), c.getTargetId(),
                        c.getCreatedAt().toEpochMilli()))
                .toList();
    }

    /** 204 when resolved; 404 when no UNCONFIRMED claim has this key (unknown, open, or already resolved). */
    @PostMapping("/{idempotencyKey}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resolve(@PathVariable String idempotencyKey,
            @RequestBody(required = false) ResolveRequest body) {
        String outcome = body == null ? null : body.outcome();
        if (!"APPLIED".equals(outcome) && !"NOT_APPLIED".equals(outcome)) {
            throw new IllegalArgumentException("outcome must be APPLIED or NOT_APPLIED");
        }
        return guard.resolve(idempotencyKey, "APPLIED".equals(outcome))
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}

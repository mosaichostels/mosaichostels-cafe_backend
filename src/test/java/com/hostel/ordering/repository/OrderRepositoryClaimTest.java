package com.hostel.ordering.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRepositoryClaimTest {

    @Test
    void claimCriteria_requiresDeliveredAndAllowsFreshOrFailedRetryInSameRoom() {
        String json = OrderRepositoryImpl.claimCriteria("o1", "106", false).getCriteriaObject().toJson();

        assertTrue(json.contains("\"status\": \"DELIVERED\""), json);
        assertTrue(json.contains("\"$or\""), json);
        assertTrue(json.contains("\"FAILED\""), json);
        assertTrue(json.contains("\"chargePostRoom\": \"106\""), json);
    }

    @Test
    void claimCriteria_refusesUnconfirmedAttemptsUnlessAcknowledged() {
        String strict = OrderRepositoryImpl.claimCriteria("o1", "106", false).getCriteriaObject().toJson();
        String acknowledged = OrderRepositoryImpl.claimCriteria("o1", "106", true).getCriteriaObject().toJson();

        assertTrue(strict.contains("UNCONFIRMED"), strict);
        assertFalse(acknowledged.contains("UNCONFIRMED"), acknowledged);
    }

    @Test
    void chargeReconcileQueryIsBoundedByCreatedAtAndLimit() {
        var q = OrderRepositoryImpl.chargeReconcileQuery(100L, 200L, 501);
        String json = q.getQueryObject().toJson();
        assertTrue(json.contains("\"createdAt\": {\"$gte\": 100, \"$lt\": 200}"), json);
        assertTrue(json.contains("\"status\": \"DELIVERED\""), json);
        assertTrue(json.contains("\"chargePostStatus\": {\"$ne\": null}"), json);
        assertTrue(q.getLimit() == 501, "limit");
        assertTrue(q.getSortObject().toJson().contains("\"createdAt\": 1"), q.getSortObject().toJson());
    }

    @Test
    void claimStampsTheTimeSoAStuckClaimCanBeFound() {
        String update = OrderRepositoryImpl.claimUpdate("106").getUpdateObject().toJson();
        assertTrue(update.contains("chargePostAt"), update);
        // without the room, a claim interrupted before its final save can never be retried
        assertTrue(update.contains("\"chargePostRoom\": \"106\""), update);
        assertTrue(update.contains("IN_PROGRESS"), update);
    }
}

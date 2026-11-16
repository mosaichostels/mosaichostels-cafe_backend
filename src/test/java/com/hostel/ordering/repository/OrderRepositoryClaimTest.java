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
    void claimStampsTheTimeSoAStuckClaimCanBeFound() {
        String update = OrderRepositoryImpl.claimUpdate("106").getUpdateObject().toJson();
        assertTrue(update.contains("chargePostAt"), update);
        // without the room, a claim interrupted before its final save can never be retried
        assertTrue(update.contains("\"chargePostRoom\": \"106\""), update);
        assertTrue(update.contains("IN_PROGRESS"), update);
    }
}

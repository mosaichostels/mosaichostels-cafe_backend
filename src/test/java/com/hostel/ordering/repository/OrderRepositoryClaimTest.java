package com.hostel.ordering.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRepositoryClaimTest {

    @Test
    void claimCriteria_requiresDeliveredAndAllowsFreshOrFailedRetryInSameRoom() {
        String json = OrderRepositoryImpl.claimCriteria("o1", "106").getCriteriaObject().toJson();

        assertTrue(json.contains("\"status\": \"DELIVERED\""), json);
        assertTrue(json.contains("\"$or\""), json);
        assertTrue(json.contains("\"FAILED\""), json);
        assertTrue(json.contains("\"chargePostRoom\": \"106\""), json);
    }
}

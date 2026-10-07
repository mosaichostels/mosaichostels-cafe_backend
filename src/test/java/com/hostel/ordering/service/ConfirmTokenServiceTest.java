package com.hostel.ordering.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmTokenServiceTest {

    @Test
    void issuedToken_isAcceptedOnceForItsScope() {
        ConfirmTokenService svc = new ConfirmTokenService();
        String token = svc.issue("delete-orders:admin");

        assertTrue(svc.consume("delete-orders:admin", token));
        assertFalse(svc.consume("delete-orders:admin", token), "a token must not be reusable");
    }

    @Test
    void token_isRejectedForAnotherScopeOrWhenMadeUp() {
        ConfirmTokenService svc = new ConfirmTokenService();
        String token = svc.issue("delete-orders:admin");

        assertFalse(svc.consume("delete-orders:other", token));
        assertFalse(svc.consume("delete-orders:admin", String.valueOf(System.currentTimeMillis())));
        assertFalse(svc.consume("delete-orders:admin", null));
    }

    @Test
    void expiredToken_isRejected() {
        ConfirmTokenService svc = new ConfirmTokenService(-1);
        String token = svc.issue("s");

        assertFalse(svc.consume("s", token));
    }

    @Test
    void tokens_areUnique() {
        ConfirmTokenService svc = new ConfirmTokenService();
        assertNotEquals(svc.issue("s"), svc.issue("s"));
    }
}

package com.hostel.ordering.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    @Test
    void blocksUsernameAfterTooManyFailures_caseInsensitively() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < 5; i++) {
            assertFalse(throttle.isBlocked("Admin"));
            throttle.recordFailure("Admin");
        }
        assertTrue(throttle.isBlocked("admin "));
    }

    @Test
    void successClearsTheCounter_andOtherUsersAreUnaffected() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < 5; i++) throttle.recordFailure("admin");
        assertFalse(throttle.isBlocked("kitchen"));

        throttle.clear("admin");
        assertFalse(throttle.isBlocked("admin"));
    }
}

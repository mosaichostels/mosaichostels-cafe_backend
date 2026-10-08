package com.hostel.ordering.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsRevocationTest {

    @Test
    void isIssuedBeforeValidFrom_matchesLogoutWatermark() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "01234567890123456789012345678901");

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new User("alice", "password", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
        );

        String token = jwtUtils.generateJwtToken(authentication);
        long issuedAt = jwtUtils.getIssuedAtMillisFromToken(token);

        assertFalse(jwtUtils.isIssuedBeforeValidFrom(token, null));
        assertFalse(jwtUtils.isIssuedBeforeValidFrom(token, issuedAt));
        assertTrue(jwtUtils.isIssuedBeforeValidFrom(token, issuedAt + 1));
    }

    @Test
    void tokenIssuedInTheSameSecondAfterTheWatermarkIsNotRejected() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "01234567890123456789012345678901");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new User("alice", "password", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))),
                null, List.of(new SimpleGrantedAuthority("ROLE_STAFF")));

        long watermark = System.currentTimeMillis();
        String token = jwtUtils.generateJwtToken(authentication);

        // iat alone is truncated to the second, which is earlier than a watermark set moments before
        assertFalse(jwtUtils.isIssuedBeforeValidFrom(token, watermark));
        assertTrue(jwtUtils.isIssuedBeforeValidFrom(token, System.currentTimeMillis() + 5_000));
    }
}

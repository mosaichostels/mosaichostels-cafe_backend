package com.hostel.ordering.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Without an explicit entry point, Spring Security 6 answers every unauthenticated
 * request with 403, which the Android client cannot tell apart from a genuine
 * "wrong role" denial - so an expired token looked like a permissions failure and
 * the app never renewed it.
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void unauthenticatedRequest_returns401NotForbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.unauthorizedEntryPoint().commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no token"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("session expired"));
    }

    @Test
    void authenticatedButWrongRole_stays403() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.accessDeniedHandler().handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("wrong role"));

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("permission"));
    }
}

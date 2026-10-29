package com.hostel.ordering.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static Set<String> reach(String role) {
        return SecurityConfig.roleHierarchy()
                .getReachableGrantedAuthorities(List.of(new SimpleGrantedAuthority(role))).stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void superAdminReachesAdmin() {
        assertTrue(reach("ROLE_SUPERADMIN").contains("ROLE_ADMIN"));
    }

    @Test
    void adminDoesNotReachSuperAdmin() {
        assertFalse(reach("ROLE_ADMIN").contains("ROLE_SUPERADMIN"));
    }

    @Test
    void staffReachesNothingElse() {
        assertEquals(Set.of("ROLE_STAFF"), reach("ROLE_STAFF"));
    }
}

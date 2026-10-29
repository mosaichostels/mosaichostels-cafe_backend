package com.hostel.ordering.service;

import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import com.hostel.ordering.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Covers the AuthService.refreshToken() call site of the revocation watermark check
 * (JwtUtils.isIssuedBeforeValidFrom), which JwtUtilsRevocationTest exercises only in
 * isolation.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRevocationTest {

    @Mock
    UserRepository userRepository;

    @Mock
    AuditService auditService;

    AuthService authService;
    JwtUtils jwtUtils;
    String token;
    long issuedAt;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtUtils, "refreshGraceMs", 31536000000L);

        authService = new AuthService();
        authService.userRepository = userRepository;
        authService.auditService = auditService;
        authService.jwtUtils = jwtUtils;
        authService.roleHierarchy = com.hostel.ordering.config.SecurityConfig.roleHierarchy();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new org.springframework.security.core.userdetails.User(
                        "alice", "password", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        token = jwtUtils.generateJwtToken(authentication);
        issuedAt = jwtUtils.getIssuedAtMillisFromToken(token);
    }

    @Test
    void refreshToken_rejectsTokenIssuedBeforeLogoutWatermark() {
        User user = new User("alice", "hashed", Set.of("ROLE_STAFF"));
        user.setTokensValidFrom(issuedAt + 1);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.refreshToken(token));
        assertEquals("Token was revoked by logout", ex.getMessage());
    }

    @Test
    void refreshToken_succeedsWhenIssuedAtOrAfterWatermark() {
        User user = new User("alice", "hashed", Set.of("ROLE_STAFF"));
        user.setTokensValidFrom(issuedAt);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Map<String, Object> result = authService.refreshToken(token);

        assertEquals("alice", result.get("username"));
    }

    @Test
    void refreshToken_succeedsWhenNeverLoggedOut() {
        User user = new User("alice", "hashed", Set.of("ROLE_STAFF"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Map<String, Object> result = authService.refreshToken(token);

        assertEquals("alice", result.get("username"));
    }

    @Test
    void refreshRolesIncludeAdminForSuperAdmin() {
        User user = new User("alice", "hashed", Set.of("ROLE_SUPERADMIN"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) authService.refreshToken(token).get("roles");

        assertEquals(Set.of("ROLE_SUPERADMIN", "ROLE_ADMIN"), Set.copyOf(roles));
        assertEquals(roles.size(), Set.copyOf(roles).size());
    }

    @Test
    void refreshRolesUnchangedForAdmin() {
        User user = new User("alice", "hashed", Set.of("ROLE_ADMIN"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertEquals(List.of("ROLE_ADMIN"), authService.refreshToken(token).get("roles"));
    }

    @Test
    void loginRolesIncludeAdminForSuperAdmin() {
        assertLoginRoles(Set.of("ROLE_SUPERADMIN"), Set.of("ROLE_SUPERADMIN", "ROLE_ADMIN"));
    }

    @Test
    void adminLoginRolesUnchanged() {
        assertLoginRoles(Set.of("ROLE_ADMIN"), Set.of("ROLE_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    private void assertLoginRoles(Set<String> stored, Set<String> expected) {
        org.springframework.security.authentication.AuthenticationManager am =
                org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);
        List<SimpleGrantedAuthority> auths = stored.stream().map(SimpleGrantedAuthority::new).toList();
        Authentication a = new UsernamePasswordAuthenticationToken(
                new org.springframework.security.core.userdetails.User("alice", "pw", auths), null, auths);
        when(am.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(a);
        authService.authenticationManager = am;

        List<String> roles = (List<String>) authService.login("alice", "pw").get("roles");

        assertEquals(expected, Set.copyOf(roles));
        assertEquals(expected.size(), roles.size());
    }
}

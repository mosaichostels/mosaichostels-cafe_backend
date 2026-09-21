package com.hostel.ordering.security;

import com.hostel.ordering.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the AuthTokenFilter.doFilterInternal() call site of the revocation watermark
 * check (JwtUtils.isIssuedBeforeValidFrom), which JwtUtilsRevocationTest exercises only
 * in isolation.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenFilterTest {

    @Mock
    UserDetailsService userDetailsService;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain filterChain;

    AuthTokenFilter filter;
    JwtUtils jwtUtils;
    String token;
    long issuedAt;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L);

        filter = new AuthTokenFilter();
        ReflectionTestUtils.setField(filter, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new org.springframework.security.core.userdetails.User(
                        "alice", "password", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        token = jwtUtils.generateJwtToken(authentication);
        issuedAt = jwtUtils.getIssuedAtMillisFromToken(token);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_rejectsTokenIssuedBeforeLogoutWatermark() throws Exception {
        User user = new User("alice", "hashed", Set.of("ROLE_STAFF"));
        user.setTokensValidFrom(issuedAt + 1);
        AuthUserDetails userDetails = new AuthUserDetails(user, List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_acceptsTokenIssuedAtOrAfterWatermark() throws Exception {
        User user = new User("alice", "hashed", Set.of("ROLE_STAFF"));
        user.setTokensValidFrom(issuedAt);
        AuthUserDetails userDetails = new AuthUserDetails(user, List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_acceptsTokenWhenNeverLoggedOut() throws Exception {
        User user = new User("alice", "hashed", Set.of("ROLE_STAFF"));
        AuthUserDetails userDetails = new AuthUserDetails(user, List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}

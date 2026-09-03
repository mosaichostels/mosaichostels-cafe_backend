package com.hostel.ordering.security;

import com.hostel.ordering.model.User;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;

/**
 * Carries the user's token revocation watermark alongside the standard credentials,
 * so AuthTokenFilter can reject a token that an explicit logout invalidated without
 * a second database lookup on every request.
 */
public class AuthUserDetails extends org.springframework.security.core.userdetails.User {

    private final Long tokensValidFrom;

    public AuthUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        super(user.getUsername(), user.getPassword(), authorities);
        this.tokensValidFrom = user.getTokensValidFrom();
    }

    /** Epoch millis before which every issued token is revoked, or null if never logged out. */
    public Long getTokensValidFrom() {
        return tokensValidFrom;
    }
}

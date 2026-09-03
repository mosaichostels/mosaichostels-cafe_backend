package com.hostel.ordering.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    HttpServletRequest request;

    @InjectMocks
    RateLimitFilter rateLimitFilter;

    @Test
    void getClientIp_remoteAddrPublic_xffPresent_returnsRemoteAddr() {
        // Public direct peer, X-Forwarded-For present and potentially forged
        // -> return direct peer unchanged (no trusted proxy; XFF not consulted)
        when(request.getRemoteAddr()).thenReturn("203.0.113.1");

        String result = rateLimitFilter.getClientIp(request);

        assertEquals("203.0.113.1", result);
    }

    @Test
    void getClientIp_remoteAddrPrivate_xffPublic_returnsXff() {
        // Private direct peer (trusted proxy), XFF contains single public IP
        // -> return public IP from XFF
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9");

        String result = rateLimitFilter.getClientIp(request);

        assertEquals("203.0.113.9", result);
    }

    @Test
    void getClientIp_remoteAddrPrivate_xffMultiple_returnsRightmostPublic() {
        // Private direct peer (trusted proxy), XFF contains multiple IPs
        // Walk right-to-left: rightmost public wins, leftmost (potentially forged) ignored
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 203.0.113.9");

        String result = rateLimitFilter.getClientIp(request);

        assertEquals("203.0.113.9", result);
    }

    @Test
    void getClientIp_remoteAddrPrivate_xffAbsent_returnsRemoteAddr() {
        // Private direct peer (trusted proxy), XFF absent
        // -> return direct peer
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        String result = rateLimitFilter.getClientIp(request);

        assertEquals("10.0.0.5", result);
    }

    @Test
    void getClientIp_remoteAddrPrivate_xffAllPrivate_returnsRemoteAddr() {
        // Private direct peer (trusted proxy), XFF contains only private/loopback IPs
        // -> return direct peer (all XFF entries are untrusted)
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");

        String result = rateLimitFilter.getClientIp(request);

        assertEquals("10.0.0.5", result);
    }
}

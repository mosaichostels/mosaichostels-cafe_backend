package com.hostel.ordering.pms;

import java.util.Map;

/** One HTTP exchange with eZee. Implementations must read at most {@code maxBytes + 1} bytes of the reply. */
public interface PmsTransport {

    /** @param body null for a request without a body (GET) */
    Reply send(String method, String url, Map<String, String> headers, String body, int maxBytes);

    record Reply(int status, String body) {}
}

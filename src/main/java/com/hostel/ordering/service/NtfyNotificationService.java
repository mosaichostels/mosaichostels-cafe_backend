package com.hostel.ordering.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Mirrors staff alerts to ntfy.sh so iOS devices (which have no FCM client here) can get them via the free ntfy app. Off unless ntfy.topic is set. */
@Service
public class NtfyNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NtfyNotificationService.class);

    private final RestClient http = RestClient.create();
    private final String topic;

    // ponytail: public ntfy.sh, the topic name is the only secret. Self-host with auth if order text must be private.
    public NtfyNotificationService(@Value("${ntfy.topic:}") String topic) {
        this.topic = topic;
    }

    /** Synchronous on purpose: callers are already @Async, and errors are swallowed so they can never break an order. */
    public void send(String title, String body, String... tags) {
        if (topic.isBlank()) return;
        try {
            // JSON publish (not headers) so emoji and non-ASCII guest names survive.
            http.post().uri("https://ntfy.sh")
                    .body(Map.of("topic", topic, "title", title, "message", body,
                            "priority", 4, "tags", List.of(tags)))
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            logger.error("❌ Failed to send ntfy notification: {}", e.getMessage());
        }
    }
}

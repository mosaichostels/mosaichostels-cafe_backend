package com.hostel.ordering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.hostel.ordering.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sends FCM push notifications via the FCM HTTP v1 REST API.
 *
 * Uses java.net.HttpURLConnection (zero extra deps) + GoogleCredentials for OAuth2 tokens.
 * This replaces the firebase-admin SDK which was causing OOM crash loops on Railway
 * due to its ~70MB transitive dependency chain (gRPC, Netty, Protobuf, etc.).
 */
@Service
@Slf4j
public class FCMNotificationService {

    private static final String FCM_URL_TEMPLATE =
            "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    private static final String ORDERS_TOPIC = "new_orders";

    private final GoogleCredentials credentials;
    private final ObjectMapper objectMapper;

    @Value("${firebase.project-id:#{null}}")
    private String projectId;

    /**
     * credentials may be null if Firebase is not configured — all methods guard against this.
     */
    public FCMNotificationService(@Autowired(required = false) GoogleCredentials credentials,
                                  ObjectMapper objectMapper) {
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    public void sendNewOrderNotification(Order order) {
        if (credentials == null) {
            log.debug("Firebase not configured — skipping notification for order {}", order.getId());
            return;
        }
        if (projectId == null || projectId.isBlank()) {
            log.warn("⚠️  firebase.project-id not set — skipping FCM notification.");
            return;
        }

        try {
            String accessToken = getAccessToken();
            String fcmUrl = String.format(FCM_URL_TEMPLATE, projectId);

            String title = "🛎 New Order Received!";
            String body = String.format("Order #%s from %s — ₹%.2f",
                    order.getId().substring(0, Math.min(8, order.getId().length())),
                    order.getBookingName(),
                    order.getTotalAmount());

            // Build FCM HTTP v1 payload
            Map<String, Object> payload = Map.of(
                "message", Map.of(
                    "topic", ORDERS_TOPIC,
                    "android", Map.of(
                        "priority", "HIGH",
                        "ttl", "3600s"
                    ),
                    "data", Map.of(
                        "type",         "NEW_ORDER",
                        "orderId",      order.getId(),
                        "customerName", order.getBookingName(),
                        "totalAmount",  String.valueOf(order.getTotalAmount()),
                        "title",        title,
                        "body",         body
                    )
                )
            );

            String jsonBody = objectMapper.writeValueAsString(payload);
            int responseCode = sendHttpPost(fcmUrl, accessToken, jsonBody);

            if (responseCode == 200) {
                log.info("✅ FCM notification sent for order {}", order.getId());
            } else {
                log.warn("⚠️  FCM returned HTTP {} for order {}", responseCode, order.getId());
            }

        } catch (Exception e) {
            // Never let a notification failure affect the order flow
            log.error("❌ FCM notification failed for order {} — order was still saved.",
                      order.getId(), e);
        }
    }

    private String getAccessToken() throws IOException {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private int sendHttpPost(String urlStr, String accessToken, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json; UTF-8");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }
}

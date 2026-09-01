package com.hostel.ordering.service;

import com.google.firebase.messaging.*;
import com.hostel.ordering.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
@Service
public class FCMNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(FCMNotificationService.class);
    private static final String ORDERS_TOPIC = "new_orders";

    private final FirebaseMessaging firebaseMessaging;

    public FCMNotificationService(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Async
    public void sendNewOrderNotification(Order order) {
        try {
            String orderId = order.getId() != null ? order.getId() : "UNKNOWN";
            String bookingName = order.getBookingName() != null ? order.getBookingName() : "Guest";
            Double totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;

            String title = "🍽 New Order Received";
            String body = bookingName + " placed an order ";

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(3600 * 1000L)
                    .build();

            WebpushConfig webpushConfig = WebpushConfig.builder()
                    .setNotification(WebpushNotification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .setIcon("/images/logo.png")
                            .build())
                    .build();

            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setAndroidConfig(androidConfig)
                    .setWebpushConfig(webpushConfig)
                    .putData("type", "NEW_ORDER")
                    .putData("orderId", orderId)
                    .putData("customerName", bookingName)
                    .putData("totalAmount", String.valueOf(totalAmount))
                    .putData("title", title)
                    .putData("body", body)
                    .build();

            String response = firebaseMessaging.send(message);
            logger.info("✅ FCM notification sent: {}", response);

        } catch (Exception e) {
            logger.error("❌ Failed to send FCM notification", e);
        }
    }

    @Async
    public void sendOrderCancelledNotification(Order order) {
        try {
            String orderId = order.getId() != null ? order.getId() : "UNKNOWN";
            String bookingName = order.getBookingName() != null ? order.getBookingName() : "Guest";

            String title = "❌ Order Cancelled!";
            String body = String.format(
                    "Order #%s for %s has been cancelled.",
                    orderId.length() >= 8 ? orderId.substring(0, 8) : orderId,
                    bookingName);

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(3600 * 1000L)
                    .build();

            WebpushConfig webpushConfig = WebpushConfig.builder()
                    .setNotification(WebpushNotification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .setIcon("/images/logo.png")
                            .build())
                    .build();

            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setAndroidConfig(androidConfig)
                    .setWebpushConfig(webpushConfig)
                    .putData("type", "ORDER_CANCELLED")
                    .putData("orderId", orderId)
                    .putData("customerName", bookingName)
                    .putData("title", title)
                    .putData("body", body)
                    .build();

            String response = firebaseMessaging.send(message);
            logger.info("✅ FCM cancellation notification sent: {}", response);

        } catch (Exception e) {
            logger.error("❌ Failed to send FCM cancellation notification", e);
        }
    }

    @Async
    public void sendAppUpdateNotification(String versionName, String downloadUrl, String releaseNotes) {
        try {
            String title = "🔄 App Update Required";
            String body = "Version " + versionName + " is now required. Tap to update.";

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(3600 * 1000L)
                    .build();

            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setAndroidConfig(androidConfig)
                    .putData("type", "APP_UPDATE")
                    .putData("versionName", versionName)
                    .putData("downloadUrl", downloadUrl)
                    .putData("releaseNotes", releaseNotes != null ? releaseNotes : "")
                    .putData("title", title)
                    .putData("body", body)
                    .build();

            String response = firebaseMessaging.send(message);
            logger.info("✅ FCM app-update notification sent: {}", response);

        } catch (Exception e) {
            logger.error("❌ Failed to send FCM app-update notification", e);
        }
    }

    public void subscribeToTopic(String token) {
        try {
            TopicManagementResponse response = firebaseMessaging.subscribeToTopic(
                    java.util.Collections.singletonList(token), ORDERS_TOPIC);
            logger.info("✅ Token subscribed to topic: {} -> {} successes", token, response.getSuccessCount());
        } catch (FirebaseMessagingException e) {
            logger.error("❌ Failed to subscribe token to topic", e);
        }
    }

    public void unsubscribeFromTopic(String token) {
        try {
            TopicManagementResponse response = firebaseMessaging.unsubscribeFromTopic(
                    java.util.Collections.singletonList(token), ORDERS_TOPIC);
            logger.info("✅ Token unsubscribed from topic: {} -> {} successes", token, response.getSuccessCount());
        } catch (FirebaseMessagingException e) {
            logger.error("❌ Failed to unsubscribe token from topic", e);
        }
    }

    @Async
    public void sendNotificationToToken(String token, String title, String body, java.util.Map<String, String> data) {
        if (token == null || token.isBlank()) {
            logger.warn("⚠️ FCM token is null or blank, skipping notification");
            return;
        }
        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .putData("title", title)
                    .putData("body", body);

            if (data != null && !data.isEmpty()) {
                for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
                    messageBuilder.putData(entry.getKey(), entry.getValue());
                }
            }

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(3600 * 1000L)
                    .build();

            Message message = messageBuilder
                    .setAndroidConfig(androidConfig)
                    .build();

            String response = firebaseMessaging.send(message);
            logger.info("✅ FCM notification sent to token: {}", response);
        } catch (FirebaseMessagingException e) {
            logger.error("❌ Failed to send FCM notification to token: {}", e.getMessage());
        }
    }
}

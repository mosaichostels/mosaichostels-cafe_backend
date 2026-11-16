package com.hostel.ordering.service;

import com.google.firebase.messaging.*;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class FCMNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(FCMNotificationService.class);
    private final FirebaseMessaging firebaseMessaging;
    private final UserRepository userRepository;

    public FCMNotificationService(FirebaseMessaging firebaseMessaging, UserRepository userRepository) {
        this.firebaseMessaging = firebaseMessaging;
        this.userRepository = userRepository;
    }

    @Async
    public void sendNewOrderNotification(Order order) {
        try {
            String orderId = order.getId() != null ? order.getId() : "UNKNOWN";
            String bookingName = order.getBookingName() != null ? order.getBookingName() : "Guest";
            Double totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;

            String title = "🍽 New Order Received";
            String dorm = order.getDormitory() != null && !order.getDormitory().isBlank() ? order.getDormitory() : "-";
            String body = bookingName + " · " + dorm + " · ₹" + String.format(java.util.Locale.US, "%.2f", totalAmount);

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

            for (String token : recipientTokens(order)) {
                Message message = Message.builder()
                        .setToken(token)
                        .setAndroidConfig(androidConfig)
                        .setWebpushConfig(webpushConfig)
                        .putData("type", "NEW_ORDER")
                        .putData("orderId", orderId)
                        .putData("customerName", bookingName)
                        .putData("totalAmount", String.valueOf(totalAmount))
                        .putData("title", title)
                        .putData("body", body)
                        .build();
                logger.info("✅ FCM notification sent: {}", firebaseMessaging.send(message));
            }

        } catch (Exception e) {
            logger.error("❌ Failed to send FCM notification", e);
        }
    }

    @Async
    public void sendOrderCancelledNotification(Order order) {
        try {
            String orderId = order.getId() != null ? order.getId() : "UNKNOWN";
            String bookingName = order.getBookingName() != null ? order.getBookingName() : "Guest";

            String title = "❌ Order Cancelled";
            String body = "Order " + shortId(orderId) + " for " + bookingName + " was cancelled";

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

            for (String token : recipientTokens(order)) {
                Message message = Message.builder()
                        .setToken(token)
                        .setAndroidConfig(androidConfig)
                        .setWebpushConfig(webpushConfig)
                        .putData("type", "ORDER_CANCELLED")
                        .putData("orderId", orderId)
                        .putData("customerName", bookingName)
                        .putData("title", title)
                        .putData("body", body)
                        .build();
                logger.info("✅ FCM cancellation notification sent: {}", firebaseMessaging.send(message));
            }

        } catch (Exception e) {
            logger.error("❌ Failed to send FCM cancellation notification", e);
        }
    }


    private Set<String> recipientTokens(Order order) {
        List<OrderItem> items = order.getItems();
        boolean essentialsOnly = items != null && !items.isEmpty()
                && items.stream().allMatch(i -> "ESSENTIAL".equals(i.getType()));
        List<String> roles = essentialsOnly
                ? List.of("ROLE_SUPERADMIN", "ROLE_ADMIN")
                : List.of("ROLE_SUPERADMIN", "ROLE_ADMIN", "ROLE_KITCHEN", "ROLE_STAFF");
        Set<String> tokens = new LinkedHashSet<>();
        for (String role : roles) {
            for (User user : userRepository.findByRolesContaining(role)) {
                if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                    tokens.add(user.getFcmToken());
                }
            }
        }
        return tokens;
    }

    /** Same short id every client shows: the last 6 characters, which are the part of an ObjectId that differs between orders placed close together. */
    static String shortId(String id) {
        if (id == null) return "#?";
        return "#" + (id.length() > 6 ? id.substring(id.length() - 6) : id).toUpperCase(java.util.Locale.ROOT);
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

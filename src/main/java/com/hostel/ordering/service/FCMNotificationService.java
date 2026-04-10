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

            String title = "🛎 New Order Received!";
            String body = String.format(
                    "Order #%s from %s — ₹%.2f",
                    orderId.length() >= 8 ? orderId.substring(0, 8) : orderId,
                    bookingName,
                    totalAmount);

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(3600 * 1000L)
                    .build();

            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setAndroidConfig(androidConfig)
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

            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setAndroidConfig(androidConfig)
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
}

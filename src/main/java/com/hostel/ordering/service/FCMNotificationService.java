package com.hostel.ordering.service;

import com.google.firebase.messaging.*;
import com.hostel.ordering.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FCMNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(FCMNotificationService.class);
    private static final String ORDERS_TOPIC = "new_orders";

    public void sendNewOrderNotification(Order order) {
        try {
            String title = "🛎 New Order Received!";
            String body = String.format(
                    "Order #%s from %s — ₹%.2f",
                    order.getId().substring(0, 8),
                    order.getBookingName(),
                    order.getTotalAmount()
            );

            // HIGH priority wakes up the device (works on all OEMs)
            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(3600 * 1000L)
                    .build();

            // Data-only message — no "notification" block.
            // This ensures onMessageReceived() fires even when app is killed
            // on Samsung, Xiaomi (MIUI), Motorola, Nothing, etc.
            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setAndroidConfig(androidConfig)
                    .putData("type", "NEW_ORDER")
                    .putData("orderId", order.getId())
                    .putData("customerName", order.getBookingName())
                    .putData("totalAmount", String.valueOf(order.getTotalAmount()))
                    .putData("title", title)   // Android app reads this to build the notification
                    .putData("body", body)     // Android app reads this to build the notification
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            logger.info("✅ FCM notification sent: {}", response);

        } catch (Exception e) {
            logger.error("❌ Failed to send FCM notification", e);
        }
    }
}
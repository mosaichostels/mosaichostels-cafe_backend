package com.hostel.ordering.service;

import com.google.firebase.messaging.*;
import com.hostel.ordering.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FCMNotificationService {

    private static final String ORDERS_TOPIC = "new_orders";

    public void sendNewOrderNotification(Order order) {
        try {
            String title = "🛎 New Order Received!";
            String body = String.format(
                    "Order #%s from %s — ₹%.2f",
                    order.getId().substring(0, 8),
                    order.getBookingName(),
                    order.getTotalAmount());

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(3600 * 1000L)
                    .build();

            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setAndroidConfig(androidConfig)
                    .putData("type", "NEW_ORDER")
                    .putData("orderId", order.getId())
                    .putData("customerName", order.getBookingName())
                    .putData("totalAmount", String.valueOf(order.getTotalAmount()))
                    .putData("title", title)
                    .putData("body", body)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ FCM notification sent: {}", response);

        } catch (Exception e) {
            log.error("❌ Failed to send FCM notification", e);
        }
    }
}
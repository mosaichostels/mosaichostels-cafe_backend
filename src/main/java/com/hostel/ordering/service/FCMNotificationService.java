package com.hostel.ordering.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.hostel.ordering.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FCMNotificationService {

    private static final String ORDERS_TOPIC = "new_orders";

    /**
     * FirebaseApp may be null if credentials are not configured (see FirebaseConfig).
     * Using @Autowired(required=false) so this service can still be constructed and the
     * app starts normally — notifications are simply skipped with a warning.
     */
    @Autowired(required = false)
    private FirebaseApp firebaseApp;

    public void sendNewOrderNotification(Order order) {
        if (firebaseApp == null) {
            log.warn("⚠️ Firebase not configured — skipping push notification for order {}", order.getId());
            return;
        }

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
            // Never let a notification failure crash the order flow
            log.error("❌ Failed to send FCM notification for order {} — order was still saved.",
                      order.getId(), e);
        }
    }
}

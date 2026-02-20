package com.hostel.ordering.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
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
            String body = String.format("Order #%s from %s — ₹%.2f",
                    order.getId().substring(0, 8),
                    order.getBookingName(),
                    order.getTotalAmount());

            Message message = Message.builder()
                    .setTopic(ORDERS_TOPIC)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("orderId", order.getId())
                    .putData("customerName", order.getBookingName())
                    .putData("total", String.valueOf(order.getTotalAmount()))
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            logger.info("FCM notification sent successfully: {}", response);

        } catch (Exception e) {
            // Don't fail the order creation if notification fails
            logger.error("Failed to send FCM notification: {}", e.getMessage());
        }
    }
}

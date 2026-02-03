package com.hostel.ordering.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.messaging.*;
import com.hostel.ordering.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class OrderService {

    private static final String COLLECTION_NAME = "orders";
    private static final String FCM_TOKENS_COLLECTION = "fcmTokens";

    @Autowired
    private Firestore firestore;

    public Order createOrder(Order order) throws ExecutionException, InterruptedException {
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());
        order.setStatus("PENDING");

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document();
        order.setId(docRef.getId());

        ApiFuture<WriteResult> result = docRef.set(order);
        result.get();

        // Send notification to Android app
        sendOrderNotification(order);

        return order;
    }

    public Order getOrder(String id) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(id);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            return document.toObject(Order.class);
        }
        return null;
    }

    public List<Order> getAllOrders() throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Order> orders = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            orders.add(document.toObject(Order.class));
        }
        return orders;
    }

    public List<Order> getOrdersByStatus(String status) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("status", status)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<Order> orders = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            orders.add(document.toObject(Order.class));
        }
        return orders;
    }

    public Order updateOrderStatus(String id, String status) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(id);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("updatedAt", System.currentTimeMillis());

        ApiFuture<WriteResult> result = docRef.update(updates);
        result.get();

        Order order = getOrder(id);

        // Send status update notification
        sendStatusUpdateNotification(order);

        return order;
    }

    public void deleteOrder(String id) throws ExecutionException, InterruptedException {
        ApiFuture<WriteResult> result = firestore.collection(COLLECTION_NAME).document(id).delete();
        result.get();
    }

    private void sendOrderNotification(Order order) {
        try {
            // Get all FCM tokens
            List<String> tokens = getFCMTokens();

            if (tokens.isEmpty()) {
                System.out.println("No FCM tokens registered");
                return;
            }

            // Create notification message
            Map<String, String> data = new HashMap<>();
            data.put("orderId", order.getId());
            data.put("bookingName", order.getBookingName());
            data.put("dormitory", order.getDormitory());
            data.put("phoneNumber", order.getPhoneNumber());
            data.put("totalAmount", String.valueOf(order.getTotalAmount()));
            data.put("status", order.getStatus());
            data.put("type", "NEW_ORDER");

            Notification notification = Notification.builder()
                    .setTitle("New Order Received!")
                    .setBody("Order from " + order.getBookingName() + " - " + order.getDormitory())
                    .build();

            // Send to all registered devices
            for (String token : tokens) {
                Message message = Message.builder()
                        .setNotification(notification)
                        .putAllData(data)
                        .setToken(token)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setSound("default")
                                        .setChannelId("orders")
                                        .build())
                                .build())
                        .build();

                try {
                    String response = FirebaseMessaging.getInstance().send(message);
                    System.out.println("Successfully sent message: " + response);
                } catch (FirebaseMessagingException e) {
                    System.err.println("Error sending message to token " + token + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendStatusUpdateNotification(Order order) {
        try {
            List<String> tokens = getFCMTokens();

            if (tokens.isEmpty()) {
                return;
            }

            Map<String, String> data = new HashMap<>();
            data.put("orderId", order.getId());
            data.put("status", order.getStatus());
            data.put("type", "STATUS_UPDATE");

            Notification notification = Notification.builder()
                    .setTitle("Order Status Updated")
                    .setBody("Order #" + order.getId().substring(0, 8) + " is now " + order.getStatus())
                    .build();

            for (String token : tokens) {
                Message message = Message.builder()
                        .setNotification(notification)
                        .putAllData(data)
                        .setToken(token)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setSound("default")
                                        .setChannelId("orders")
                                        .build())
                                .build())
                        .build();

                try {
                    FirebaseMessaging.getInstance().send(message);
                } catch (FirebaseMessagingException e) {
                    System.err.println("Error sending status update: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending status notification: " + e.getMessage());
        }
    }

    private List<String> getFCMTokens() {
        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(FCM_TOKENS_COLLECTION).get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            List<String> tokens = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) {
                String token = document.getString("token");
                if (token != null && !token.isEmpty()) {
                    tokens.add(token);
                }
            }
            return tokens;
        } catch (Exception e) {
            System.err.println("Error getting FCM tokens: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void registerFCMToken(String token) throws ExecutionException, InterruptedException {
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("createdAt", System.currentTimeMillis());

        firestore.collection(FCM_TOKENS_COLLECTION).document(token).set(data).get();
    }

    public void unregisterFCMToken(String token) throws ExecutionException, InterruptedException {
        firestore.collection(FCM_TOKENS_COLLECTION).document(token).delete().get();
    }
}

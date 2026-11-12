package com.hostel.ordering.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FCMNotificationTopicTest {

    private static Order orderOf(String... types) {
        List<OrderItem> items = new ArrayList<>();
        for (String t : types) {
            OrderItem item = new OrderItem();
            item.setType(t);
            items.add(item);
        }
        Order order = new Order();
        order.setItems(items);
        return order;
    }

    @Test
    void menuItemGoesToOrdersTopic() {
        assertEquals("new_orders", FCMNotificationService.topicFor(orderOf("MENU")));
    }

    @Test
    void essentialOnlyGoesToEssentialsTopic() {
        assertEquals("new_essential_orders", FCMNotificationService.topicFor(orderOf("ESSENTIAL")));
        assertEquals("new_essential_orders", FCMNotificationService.topicFor(orderOf("ESSENTIAL", "ESSENTIAL")));
    }

    @Test
    void mixedOrderGoesToOrdersTopic() {
        assertEquals("new_orders", FCMNotificationService.topicFor(orderOf("MENU", "ESSENTIAL")));
    }

    @Test
    void nullItemTypeCountsAsMenu() {
        assertEquals("new_orders", FCMNotificationService.topicFor(orderOf((String) null)));
    }

    @Test
    void emptyOrNullItemListGoesToOrdersTopic() {
        assertEquals("new_orders", FCMNotificationService.topicFor(orderOf()));
        Order order = new Order();
        order.setItems(null);
        assertEquals("new_orders", FCMNotificationService.topicFor(order));
    }

    @Test
    void cancelledAndNewPushesUseTheSameTopicRule() throws Exception {
        FirebaseMessaging fcm = mock(FirebaseMessaging.class);
        FCMNotificationService service = new FCMNotificationService(fcm);

        service.sendOrderCancelledNotification(orderOf("ESSENTIAL"));
        service.sendOrderCancelledNotification(orderOf("MENU", "ESSENTIAL"));
        service.sendNewOrderNotification(orderOf("ESSENTIAL"));

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(fcm, org.mockito.Mockito.times(3)).send(sent.capture());
        assertEquals("new_essential_orders", topicOf(sent.getAllValues().get(0)));
        assertEquals("new_orders", topicOf(sent.getAllValues().get(1)));
        assertEquals("new_essential_orders", topicOf(sent.getAllValues().get(2)));
    }

    // Message exposes no topic getter; read the private field rather than send to Firebase.
    private static String topicOf(Message message) throws Exception {
        Field f = Message.class.getDeclaredField("topic");
        f.setAccessible(true);
        return (String) f.get(message);
    }
}

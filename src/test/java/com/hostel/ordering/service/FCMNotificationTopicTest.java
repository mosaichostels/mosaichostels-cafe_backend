package com.hostel.ordering.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class FCMNotificationRecipientTest {

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
    void menuOrdersGoToCurrentAdminKitchenAndLegacyStaffTokensOnce() throws Exception {
        FirebaseMessaging fcm = mock(FirebaseMessaging.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByRolesContaining("ROLE_SUPERADMIN")).thenReturn(List.of(user("shared")));
        when(users.findByRolesContaining("ROLE_ADMIN")).thenReturn(List.of(user("admin"), user("shared")));
        when(users.findByRolesContaining("ROLE_KITCHEN")).thenReturn(List.of(user("kitchen")));
        when(users.findByRolesContaining("ROLE_STAFF")).thenReturn(List.of(user("legacy")));
        FCMNotificationService service = new FCMNotificationService(fcm, users);

        service.sendNewOrderNotification(orderOf("MENU"));

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(fcm, org.mockito.Mockito.times(4)).send(sent.capture());
        assertEquals(Set.of("shared", "admin", "kitchen", "legacy"), tokensOf(sent.getAllValues()));
    }

    @Test
    void essentialsExcludeKitchenAndDemotedUsersStopReceivingPushes() throws Exception {
        FirebaseMessaging fcm = mock(FirebaseMessaging.class);
        UserRepository users = mock(UserRepository.class);
        User admin = user("admin");
        when(users.findByRolesContaining("ROLE_SUPERADMIN")).thenReturn(List.of());
        when(users.findByRolesContaining("ROLE_ADMIN")).thenReturn(List.of(admin), List.of());
        when(users.findByRolesContaining("ROLE_KITCHEN")).thenReturn(List.of(user("kitchen")));
        FCMNotificationService service = new FCMNotificationService(fcm, users);

        service.sendOrderCancelledNotification(orderOf("ESSENTIAL"));

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(fcm).send(sent.capture());
        assertEquals("admin", tokenOf(sent.getValue()));

        clearInvocations(fcm);
        service.sendNewOrderNotification(orderOf("ESSENTIAL"));
        verify(fcm, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.any());
    }

    private static User user(String token) {
        User user = new User();
        user.setFcmToken(token);
        return user;
    }

    private static Set<String> tokensOf(List<Message> messages) {
        return messages.stream().map(message -> {
            try {
                return tokenOf(message);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }).collect(java.util.stream.Collectors.toSet());
    }

    // Message exposes no token getter; read the private field rather than send to Firebase.
    private static String tokenOf(Message message) throws ReflectiveOperationException {
        Field f = Message.class.getDeclaredField("token");
        f.setAccessible(true);
        return (String) f.get(message);
    }
}

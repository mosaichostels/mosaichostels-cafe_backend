package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.repository.MenuItemRepository;
import com.hostel.ordering.repository.OrderRepository;
import com.hostel.ordering.repository.OtherEssentialRepository;
import com.hostel.ordering.ezee.EzeeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    MenuItemRepository menuItemRepository;

    @Mock
    OtherEssentialRepository otherEssentialRepository;

    @Mock
    EzeeClient ezeeClient;

    @Mock
    OrderRepository orderRepository;

    @Mock
    FCMNotificationService fcmNotificationService;

    @Mock
    AuditService auditService;

    @Mock
    OrderStatusService orderStatusService;

    @Mock
    com.hostel.ordering.ezee.EzeeChargePostService ezeeChargePostService;

    @Mock
    com.hostel.ordering.repository.UserRepository userRepository;

    @Mock
    IdempotencyService idempotencyService;

    @Mock
    DormitoryService dormitoryService;

    @InjectMocks
    OrderService orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setItems(new ArrayList<>());
        orderService = new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, null, ezeeClient, userRepository, idempotencyService, dormitoryService);
    }

    @Test
    void repriceOrder_withTamperedTotal_recomputesCorrectly() {
        // Client sends tampered totalAmount=999999, but actual price is 100
        OrderItem item = new OrderItem();
        item.setMenuItemId("item1");
        item.setMenuItemName("Test Item");
        item.setQuantity(2);
        item.setPrice(999999.0);
        item.setSubtotal(999999.0);
        order.setItems(List.of(item));
        order.setTotalAmount(999999.0);

        com.hostel.ordering.model.MenuItem menuItem = new com.hostel.ordering.model.MenuItem();
        menuItem.setId("item1");
        menuItem.setName("Test");
        menuItem.setPrice(100.0);
        when(menuItemRepository.findById("item1")).thenReturn(Optional.of(menuItem));

        orderService.repriceOrder(order);

        assertEquals(200.0, order.getTotalAmount(), "Server should recompute total as 100*2=200");
        assertEquals(100.0, item.getPrice(), "Server should set price to 100");
        assertEquals(200.0, item.getSubtotal(), "Server should set subtotal to 200");
    }

    @Test
    void repriceOrder_withUnknownItem_throwsException() {
        OrderItem item = new OrderItem();
        item.setMenuItemId("unknown");
        item.setMenuItemName("Unknown");
        item.setQuantity(1);
        order.setItems(List.of(item));

        when(menuItemRepository.findById("unknown")).thenReturn(Optional.empty());
        when(otherEssentialRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> orderService.repriceOrder(order),
                "Should throw exception for unknown item");
    }

    @Test
    void repriceOrder_withInvalidQuantity_throwsException() {
        OrderItem item = new OrderItem();
        item.setMenuItemId("item1");
        item.setMenuItemName("Test");
        item.setQuantity(0);
        order.setItems(List.of(item));

        assertThrows(IllegalArgumentException.class, () -> orderService.repriceOrder(order),
                "Should throw exception for quantity < 1");
    }

    @Test
    void repriceOrder_withTooManyItems_throwsException() {
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            OrderItem item = new OrderItem();
            item.setMenuItemId("item" + i);
            item.setMenuItemName("Item " + i);
            item.setQuantity(1);
            items.add(item);
        }
        order.setItems(items);

        assertThrows(IllegalArgumentException.class, () -> orderService.repriceOrder(order),
                "Should throw exception for >50 items");
    }

    @Test
    void searchEzeeCandidates_byName_returnsMatchingRowsCaseInsensitive() {
        OrderService svc = new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, null, ezeeClient, userRepository, idempotencyService, dormitoryService);

        LinkedHashMap<String, String> row1 = new LinkedHashMap<>();
        row1.put("guestname", "Mr. Joy");
        row1.put("room", "106");
        row1.put("roomtype", "8 - Bed Mixed Dorm");

        LinkedHashMap<String, String> row2 = new LinkedHashMap<>();
        row2.put("guestname", "Mrs Sophia");
        row2.put("room", "201");
        row2.put("roomtype", "101 - Private Room");

        when(ezeeClient.postForRoomRows(org.mockito.ArgumentMatchers.argThat(
                m -> m != null && "roomlist".equals(m.get("oprn")))))
                .thenReturn(List.of(row1, row2));

        List<Map<String, String>> result = svc.searchEzeeCandidates("joy");

        assertEquals(1, result.size());
        assertEquals("Mr. Joy", result.get(0).get("guestname"));
    }

    @Test
    void searchEzeeCandidates_blankName_returnsWholeRoomlist() {
        OrderService svc = new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, null, ezeeClient, userRepository, idempotencyService, dormitoryService);

        LinkedHashMap<String, String> row1 = new LinkedHashMap<>();
        row1.put("guestname", "Mr. Joy");
        row1.put("room", "106");

        when(ezeeClient.postForRoomRows(org.mockito.ArgumentMatchers.argThat(
                m -> m != null && "roomlist".equals(m.get("oprn")))))
                .thenReturn(List.of(row1));

        List<Map<String, String>> result = svc.searchEzeeCandidates(null);

        assertEquals(1, result.size());
    }

    @Test
    void searchEzeeCandidates_ezeeThrows_surfacesUnavailableInsteadOfEmptyList() {
        OrderService svc = new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, null, ezeeClient, userRepository, idempotencyService, dormitoryService);

        when(ezeeClient.postForRoomRows(org.mockito.ArgumentMatchers.argThat(
                m -> m != null && "roomlist".equals(m.get("oprn")))))
                .thenThrow(new IllegalStateException("connection refused"));

        assertThrows(com.hostel.ordering.ezee.EzeeUnavailableException.class, () -> svc.searchEzeeCandidates("joy"));
    }

    @Test
    void postChargeForOrder_ezeeAccepts_savesQueuedAndSetsChecked() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient, userRepository, idempotencyService, dormitoryService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setBookingName("Test Guest");

        when(orderRepository.claimForChargePost("order1", "106", false)).thenReturn(existing);
        when(ezeeChargePostService.post(existing, "106")).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setChargePostStatus("QUEUED");
            o.setChargePostRequestId("2805");
            return o;
        });
        when(orderRepository.save(existing)).thenReturn(existing);

        Order result = svc.postChargeForOrder("order1", "106", "staff1", false);

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("CHECKED", result.getStatus());
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.times(1)).save(existing);
    }

    @Test
    void postChargeForOrder_ezeeRejects_savesFailedAndLeavesStatusUnchanged() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient, userRepository, idempotencyService, dormitoryService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setBookingName("Test Guest");

        when(orderRepository.claimForChargePost("order1", "106", false)).thenReturn(existing);
        when(ezeeChargePostService.post(existing, "106")).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setChargePostStatus("FAILED");
            o.setChargePostError("Room not occupied");
            return o;
        });
        when(orderRepository.save(existing)).thenReturn(existing);

        Order result = svc.postChargeForOrder("order1", "106", "staff1", false);

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("DELIVERED", result.getStatus());
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.times(1)).save(existing);
    }

    @Test
    void postChargeForOrder_alreadyQueued_returnsUnchangedWithoutCallingEzee() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient, userRepository, idempotencyService, dormitoryService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("CHECKED");
        existing.setChargePostStatus("QUEUED");
        existing.setChargePostRequestId("2805");

        // claimForChargePost returns null because chargePostStatus is already QUEUED
        when(orderRepository.claimForChargePost("order1", "106", false)).thenReturn(null);
        when(orderRepository.findById("order1")).thenReturn(java.util.Optional.of(existing));

        Order result = svc.postChargeForOrder("order1", "106", "staff1", false);

        // Result should be the existing order without calling eZee
        assertNotNull(result);
        assertEquals("QUEUED", result.getChargePostStatus());
        org.mockito.Mockito.verifyNoInteractions(ezeeChargePostService);
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void postChargeForOrder_unknownOrder_returnsNull() {
        OrderService svc = new OrderService(orderRepository, null, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient, userRepository, idempotencyService, dormitoryService);

        when(orderRepository.findById("missing")).thenReturn(java.util.Optional.empty());

        Order result = svc.postChargeForOrder("missing", "106", "staff1", false);

        assertNull(result);
    }

    @Test
    void updateOrderStatus_toCancelled_chargeQueued_voidsIt() {
        OrderService svc = new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient, userRepository, idempotencyService, dormitoryService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("CHECKED");
        existing.setChargePostStatus("QUEUED");
        existing.setChargePostRequestId("2805");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CANCELLED", "Cancelled", "cancelled", false)));
        when(orderRepository.findById("order1")).thenReturn(java.util.Optional.of(existing));
        when(ezeeChargePostService.voidPost(existing)).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setChargePostStatus("VOIDED");
            o.setChargePostError(null);
            return o;
        });
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CANCELLED", "staff1");

        org.mockito.Mockito.verify(ezeeChargePostService).voidPost(existing);
        org.mockito.Mockito.verify(auditService, org.mockito.Mockito.never())
                .logAction(org.mockito.ArgumentMatchers.eq("ORDER_CHARGEPOST_VOID_FAILED"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateOrderStatus_toCancelled_voidFails_logsAuditEntry() {
        OrderService svc = new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient, userRepository, idempotencyService, dormitoryService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("CHECKED");
        existing.setBookingName("Test Guest");
        existing.setChargePostStatus("QUEUED");
        existing.setChargePostRequestId("2805");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CANCELLED", "Cancelled", "cancelled", false)));
        when(orderRepository.findById("order1")).thenReturn(java.util.Optional.of(existing));
        when(ezeeChargePostService.voidPost(existing)).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setChargePostStatus("QUEUED");
            o.setChargePostError("Void failed: requestid not found");
            return o;
        });
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CANCELLED", "staff1");

        org.mockito.Mockito.verify(auditService).logAction(
                org.mockito.ArgumentMatchers.eq("ORDER_CHARGEPOST_VOID_FAILED"),
                org.mockito.ArgumentMatchers.contains("Void failed: requestid not found"));
    }

    @Test
    void updateOrderStatus_toCancelled_chargeNotQueued_doesNotCallVoid() {
        OrderService svc = new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient, userRepository, idempotencyService, dormitoryService);

        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");

        when(orderStatusService.getAllStatuses()).thenReturn(List.of(
                new com.hostel.ordering.model.OrderStatusConfig("CANCELLED", "Cancelled", "cancelled", false)));
        when(orderRepository.findById("order1")).thenReturn(java.util.Optional.of(existing));
        when(orderRepository.save(existing)).thenReturn(existing);

        svc.updateOrderStatus("order1", "CANCELLED", "staff1");

        org.mockito.Mockito.verifyNoInteractions(ezeeChargePostService);
    }

    private OrderService chargeService() {
        return new OrderService(orderRepository, fcmNotificationService, auditService, orderStatusService,
                menuItemRepository, otherEssentialRepository, ezeeChargePostService, ezeeClient, userRepository, idempotencyService, dormitoryService);
    }

    @Test
    void postChargeForOrder_orderNotDelivered_isRejected() {
        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("CANCELLED");
        when(orderRepository.claimForChargePost("order1", "106", false)).thenReturn(null);
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> chargeService().postChargeForOrder("order1", "106", "admin", false));
        org.mockito.Mockito.verifyNoInteractions(ezeeChargePostService);
    }

    @Test
    void postChargeForOrder_failedPartialCharge_retryInOtherRoomIsRejected() {
        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setChargePostStatus("FAILED");
        existing.setChargePostRoom("106");
        existing.setChargePostedItems(new ArrayList<>(List.of("a#0")));
        when(orderRepository.claimForChargePost("order1", "107", false)).thenReturn(null);
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> chargeService().postChargeForOrder("order1", "107", "admin", false));
        org.mockito.Mockito.verifyNoInteractions(ezeeChargePostService);
    }

    @Test
    void postChargeForOrder_postThrows_marksFailedAndKeepsPostedItems() {
        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setChargePostedItems(new ArrayList<>(List.of("a#0")));
        when(orderRepository.claimForChargePost("order1", "106", false)).thenReturn(existing);
        when(ezeeChargePostService.post(existing, "106")).thenThrow(new RuntimeException("boom"));
        when(orderRepository.save(existing)).thenReturn(existing);

        Order result = chargeService().postChargeForOrder("order1", "106", "admin", false);

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals(List.of("a#0"), result.getChargePostedItems());
    }

    @Test
    void repriceOrder_overwritesClientSuppliedItemName() {
        OrderItem item = new OrderItem();
        item.setMenuItemId("item1");
        item.setMenuItemName("Room upgrade");
        item.setQuantity(1);
        order.setItems(List.of(item));
        com.hostel.ordering.model.MenuItem menuItem = new com.hostel.ordering.model.MenuItem();
        menuItem.setName("Aloo Paratha");
        menuItem.setPrice(80.0);
        when(menuItemRepository.findById("item1")).thenReturn(Optional.of(menuItem));

        orderService.repriceOrder(order);

        assertEquals("Aloo Paratha", item.getMenuItemName());
    }

    @Test
    void createOrder_unknownDormitory_isRejected() {
        when(dormitoryService.getAllDormitories()).thenReturn(List.of(new com.hostel.ordering.model.Dormitory("8 - Bed Mixed Dorm")));
        com.hostel.ordering.dto.CreateOrderRequest req = new com.hostel.ordering.dto.CreateOrderRequest();
        req.setBookingName("Joy");
        req.setDormitory("Dorm Z");
        req.setItems(new ArrayList<>());

        assertThrows(IllegalArgumentException.class, () -> chargeService().createOrder(req, "GUEST"));
    }

    private Order charged(String id, String status, String chargeStatus) {
        Order o = new Order();
        o.setId(id);
        o.setStatus(status);
        o.setChargePostStatus(chargeStatus);
        return o;
    }

    @Test
    void deleteOrder_withPostedCharge_isRejected() {
        when(orderRepository.findById("o1")).thenReturn(Optional.of(charged("o1", "CHECKED", "QUEUED")));

        assertThrows(IllegalArgumentException.class, () -> chargeService().deleteOrder("o1"));
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteAllOrders_skipsPendingAndChargedOrders() {
        Order done = charged("done", "DELIVERED", null);
        when(orderRepository.findAll()).thenReturn(List.of(
                done, charged("pending", "ORDERED", null), charged("posted", "CHECKED", "QUEUED")));

        int deleted = chargeService().deleteAllOrders();

        assertEquals(1, deleted);
        org.mockito.Mockito.verify(orderRepository).deleteAll(List.of(done));
    }

    @Test
    void postChargeForOrder_unconfirmedEarlierAttempt_needsAcknowledgement() {
        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        existing.setChargePostStatus("FAILED");
        existing.setChargePostedItems(new ArrayList<>(List.of("UNCONFIRMED:a#0")));
        when(orderRepository.claimForChargePost("order1", "106", false)).thenReturn(null);
        when(orderRepository.findById("order1")).thenReturn(Optional.of(existing));

        assertThrows(com.hostel.ordering.exception.ChargeUnconfirmedException.class,
                () -> chargeService().postChargeForOrder("order1", "106", "admin", false));
        org.mockito.Mockito.verifyNoInteractions(ezeeChargePostService);
    }

    @Test
    void postChargeForOrder_acknowledgementIsPassedToTheClaim() {
        Order existing = new Order();
        existing.setId("order1");
        existing.setStatus("DELIVERED");
        when(orderRepository.claimForChargePost("order1", "106", true)).thenReturn(existing);
        when(ezeeChargePostService.post(existing, "106")).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setChargePostStatus("QUEUED");
            return o;
        });
        when(orderRepository.save(existing)).thenReturn(existing);

        assertEquals("QUEUED", chargeService().postChargeForOrder("order1", "106", "admin", true).getChargePostStatus());
    }
}

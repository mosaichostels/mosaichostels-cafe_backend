package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.repository.MenuItemRepository;
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

    @InjectMocks
    OrderService orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setItems(new ArrayList<>());
        orderService = new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, ezeeClient);
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
    void searchEzeeCandidates_byRoom_wrapsRoomqueryAsSingleRow() {
        OrderService svc = new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, ezeeClient);

        LinkedHashMap<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("guestname", "Denial Mark");
        roomqueryResponse.put("room", "106");
        roomqueryResponse.put("masterfolio", "10");
        when(ezeeClient.post(org.mockito.ArgumentMatchers.argThat(
                m -> m != null && "roomquery".equals(m.get("oprn"))))).thenReturn(roomqueryResponse);

        List<Map<String, String>> result = svc.searchEzeeCandidates("106", null);

        assertEquals(1, result.size());
        assertEquals("Denial Mark", result.get(0).get("guestname"));
    }

    @Test
    void searchEzeeCandidates_byRoom_roomqueryFails_returnsEmptyList() {
        OrderService svc = new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, ezeeClient);

        LinkedHashMap<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "error");
        when(ezeeClient.post(org.mockito.ArgumentMatchers.argThat(
                m -> m != null && "roomquery".equals(m.get("oprn"))))).thenReturn(roomqueryResponse);

        List<Map<String, String>> result = svc.searchEzeeCandidates("106", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void searchEzeeCandidates_noRoom_returnsWholeRoomlistFilteredByDormitory() {
        OrderService svc = new OrderService(null, null, null, null, menuItemRepository, otherEssentialRepository, ezeeClient);

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

        List<Map<String, String>> result = svc.searchEzeeCandidates(null, "8 - Bed Mixed Dorm");

        assertEquals(1, result.size());
        assertEquals("Mr. Joy", result.get(0).get("guestname"));
    }
}
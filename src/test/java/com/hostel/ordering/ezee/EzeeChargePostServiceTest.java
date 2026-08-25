package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EzeeChargePostServiceTest {

    @Mock
    EzeeClient ezeeClient;

    private EzeeChargePostService service;

    private Order sampleOrder() {
        Order order = new Order();
        order.setId("order123");
        order.setBookingName("Denial Mark");
        order.setDormitory("8 - Bed Mixed Dorm");
        order.setUpdatedBy("staff1");
        order.setTotalAmount(250.0);
        OrderItem item = new OrderItem();
        item.setMenuItemName("Aloo Paratha");
        item.setQuantity(2);
        item.setSubtotal(160.0);
        order.setItems(List.of(item));
        return order;
    }

    // roomquery scoped to a single room returns rows with no "room" tag —
    // occupancy is per-bed via masterfolio/resno, not a repeated room field.
    private Map<String, String> occupantRow(String masterfolio, String resno) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("masterfolio", masterfolio);
        row.put("resno", resno);
        return row;
    }

    @Test
    void post_roomHasSingleOccupant_extraChargeSucceeds_marksQueued() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        when(ezeeClient.postRoomQuery(any()))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of(occupantRow("8", "1001"))));

        Map<String, String> extraChargeResponse = new LinkedHashMap<>();
        extraChargeResponse.put("status", "ok");
        extraChargeResponse.put("msg", "Extra charge is added successfully");
        when(ezeeClient.postExtraCharge(eq("1001"), eq("8"), eq("FOOD1"), eq("160.00"), eq("1"), any()))
                .thenReturn(extraChargeResponse);

        Order result = service.post(sampleOrder(), "106");

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("106", result.getChargePostRoom());
        assertEquals("8", result.getChargePostFolio());
        assertNull(result.getChargePostError());
        assertNotNull(result.getChargePostAt());
    }

    @Test
    void post_mixedCart_postsMenuAndEssentialAsSeparateCharges_marksQueued() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");

        Order order = sampleOrder();
        OrderItem essential = new OrderItem();
        essential.setMenuItemName("Toothbrush");
        essential.setQuantity(1);
        essential.setSubtotal(90.0);
        essential.setType("ESSENTIAL");
        order.setItems(List.of(order.getItems().get(0), essential));

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        when(ezeeClient.postRoomQuery(any()))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of(occupantRow("8", "1001"))));

        Map<String, String> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        when(ezeeClient.postExtraCharge(eq("1001"), eq("8"), eq("FOOD1"), eq("160.00"), eq("1"), any()))
                .thenReturn(ok);
        when(ezeeClient.postExtraCharge(eq("1001"), eq("8"), eq("ESSENTIAL1"), eq("90.00"), eq("1"), any()))
                .thenReturn(ok);

        Order result = service.post(order, "106");

        assertEquals("QUEUED", result.getChargePostStatus());
        assertNull(result.getChargePostError());
    }

    @Test
    void post_essentialItemWithoutChargeIdConfigured_marksFailed() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "");

        Order order = sampleOrder();
        OrderItem essential = new OrderItem();
        essential.setMenuItemName("Toothbrush");
        essential.setQuantity(1);
        essential.setSubtotal(90.0);
        essential.setType("ESSENTIAL");
        order.setItems(List.of(essential));

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        when(ezeeClient.postRoomQuery(any()))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of(occupantRow("8", "1001"))));

        Order result = service.post(order, "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertTrue(result.getChargePostError().contains("Essential charge id not configured"));
    }

    @Test
    void post_roomqueryFails_marksFailedWithoutCallingExtraCharge() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "error");
        roomqueryResponse.put("msg", "Room not occupied");
        when(ezeeClient.postRoomQuery(any()))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of()));

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Room not occupied", result.getChargePostError());
        assertNull(result.getChargePostRequestId());
    }

    @Test
    void post_noOccupantForRoom_marksFailed() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        when(ezeeClient.postRoomQuery(any()))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of()));

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("No occupant found for room 106", result.getChargePostError());
    }

    @Test
    void post_ezeeReturnsExtraChargeError_marksFailed() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        when(ezeeClient.postRoomQuery(any()))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of(occupantRow("8", "1001"))));

        Map<String, String> extraChargeResponse = new LinkedHashMap<>();
        extraChargeResponse.put("status", "error");
        extraChargeResponse.put("msg", "Charge Id is missing for booking 1001");
        when(ezeeClient.postExtraCharge(eq("1001"), eq("8"), eq("FOOD1"), eq("160.00"), eq("1"), any()))
                .thenReturn(extraChargeResponse);

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Charge Id is missing for booking 1001", result.getChargePostError());
        assertEquals("106", result.getChargePostRoom());
        assertEquals("8", result.getChargePostFolio());
    }

    @Test
    void post_orderHasNullTotalAmount_marksFailedWithoutCallingEzee() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");
        Order order = sampleOrder();
        order.setTotalAmount(null);

        Order result = service.post(order, "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Order has no total amount", result.getChargePostError());
        assertNull(result.getChargePostRequestId());
        verifyNoInteractions(ezeeClient);
    }

    @Test
    void post_orderAlreadyQueued_returnsUnchangedWithoutCallingEzee() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");
        Order order = sampleOrder();
        order.setChargePostStatus("QUEUED");
        order.setChargePostRequestId("2805");

        Order result = service.post(order, "106");

        assertSame(order, result);
        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("2805", result.getChargePostRequestId());
        verifyNoInteractions(ezeeClient);
    }

    @Test
    void post_roomHasMultipleOccupantsOnDifferentFolios_marksFailedAsAmbiguous() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        when(ezeeClient.postRoomQuery(any()))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of(
                        occupantRow("10", "1001"),
                        occupantRow("11", "1002"))));

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertTrue(result.getChargePostError().contains("multiple occupants"));
    }

    @Test
    void post_retryAfterPartialFailure_skipsAlreadyPostedGroupAndOnlyRetriesFailedOne() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");

        Order order = sampleOrder();
        OrderItem essential = new OrderItem();
        essential.setMenuItemName("Toothbrush");
        essential.setQuantity(1);
        essential.setSubtotal(90.0);
        essential.setType("ESSENTIAL");
        order.setItems(List.of(order.getItems().get(0), essential));
        // Simulate a prior attempt where the MENU group already posted
        // successfully and the ESSENTIAL group failed.
        order.setChargePostStatus("FAILED");
        order.setChargePostedGroups(new java.util.ArrayList<>(List.of("MENU")));

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        when(ezeeClient.postRoomQuery(any()))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of(occupantRow("8", "1001"))));

        Map<String, String> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        when(ezeeClient.postExtraCharge(eq("1001"), eq("8"), eq("ESSENTIAL1"), eq("90.00"), eq("1"), any()))
                .thenReturn(ok);

        Order result = service.post(order, "106");

        assertEquals("QUEUED", result.getChargePostStatus());
        assertTrue(result.getChargePostedGroups().containsAll(List.of("MENU", "ESSENTIAL")));
        // MENU already succeeded on the prior attempt — must not be posted again.
        org.mockito.Mockito.verify(ezeeClient, org.mockito.Mockito.never())
                .postExtraCharge(any(), any(), eq("FOOD1"), any(), any(), any());
    }

    @Test
    void voidPost_recordsManualRemovalInstructionWithoutCallingEzee() {
        service = new EzeeChargePostService(ezeeClient, "FOOD1", "ESSENTIAL1");
        Order order = sampleOrder();
        order.setChargePostStatus("QUEUED");
        order.setChargePostFolio("8");

        Order result = service.voidPost(order);

        assertEquals("QUEUED", result.getChargePostStatus());
        assertTrue(result.getChargePostError().contains("Food Charge"));
        assertTrue(result.getChargePostError().contains("8"));
        verifyNoInteractions(ezeeClient);
    }
}

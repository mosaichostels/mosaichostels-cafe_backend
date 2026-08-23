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

    @Test
    void post_roomHasLiveFolio_chargepostSucceeds_marksQueued() {
        service = new EzeeChargePostService(ezeeClient, "Cafe", "Asia/Kolkata");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("guestname", "Denial Mark");
        roomqueryResponse.put("masterfolio", "8");
        when(ezeeClient.postRoomQuery(argWithOprn("roomquery")))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of()));

        Map<String, String> chargepostResponse = new LinkedHashMap<>();
        chargepostResponse.put("status", "ok");
        chargepostResponse.put("msg", "added in queue");
        chargepostResponse.put("requestid", "2805");
        when(ezeeClient.post(argWithOprn("chargepost"))).thenReturn(chargepostResponse);

        Order result = service.post(sampleOrder(), "106");

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("2805", result.getChargePostRequestId());
        assertEquals("106", result.getChargePostRoom());
        assertEquals("8", result.getChargePostFolio());
        assertNull(result.getChargePostError());
        assertNotNull(result.getChargePostAt());
    }

    @Test
    void post_roomHasNoLiveFolio_marksFailedWithoutCallingChargepost() {
        service = new EzeeChargePostService(ezeeClient, "Cafe", "Asia/Kolkata");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "error");
        roomqueryResponse.put("msg", "Room not occupied");
        when(ezeeClient.postRoomQuery(argWithOprn("roomquery")))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of()));

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Room not occupied", result.getChargePostError());
        assertNull(result.getChargePostRequestId());
    }

    @Test
    void post_ezeeReturnsChargepostError_marksFailed() {
        service = new EzeeChargePostService(ezeeClient, "Cafe", "Asia/Kolkata");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("guestname", "Denial Mark");
        roomqueryResponse.put("masterfolio", "8");
        when(ezeeClient.postRoomQuery(argWithOprn("roomquery")))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of()));

        Map<String, String> chargepostResponse = new LinkedHashMap<>();
        chargepostResponse.put("status", "error");
        chargepostResponse.put("msg", "Folio not found in PMS");
        when(ezeeClient.post(argWithOprn("chargepost"))).thenReturn(chargepostResponse);

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Folio not found in PMS", result.getChargePostError());
    }

    @Test
    void voidPost_ezeeConfirms_marksVoided() {
        service = new EzeeChargePostService(ezeeClient, "Cafe", "Asia/Kolkata");
        Order order = sampleOrder();
        order.setChargePostStatus("QUEUED");
        order.setChargePostRequestId("2805");

        Map<String, String> voidResponse = new LinkedHashMap<>();
        voidResponse.put("status", "ok");
        when(ezeeClient.post(argWithOprn("voidcharge"))).thenReturn(voidResponse);

        Order result = service.voidPost(order);

        assertEquals("VOIDED", result.getChargePostStatus());
        assertNull(result.getChargePostError());
    }

    @Test
    void voidPost_ezeeRejects_leavesStatusQueuedAndRecordsError() {
        service = new EzeeChargePostService(ezeeClient, "Cafe", "Asia/Kolkata");
        Order order = sampleOrder();
        order.setChargePostStatus("QUEUED");
        order.setChargePostRequestId("2805");

        Map<String, String> voidResponse = new LinkedHashMap<>();
        voidResponse.put("status", "error");
        voidResponse.put("msg", "requestid not found");
        when(ezeeClient.post(argWithOprn("voidcharge"))).thenReturn(voidResponse);

        Order result = service.voidPost(order);

        assertEquals("QUEUED", result.getChargePostStatus());
        assertEquals("Void failed: requestid not found", result.getChargePostError());
    }

    @Test
    void post_orderHasNullTotalAmount_marksFailedWithoutCallingEzee() {
        service = new EzeeChargePostService(ezeeClient, "Cafe", "Asia/Kolkata");
        Order order = sampleOrder();
        order.setTotalAmount(null);

        Order result = service.post(order, "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertEquals("Order has no total amount", result.getChargePostError());
        assertNull(result.getChargePostRequestId());
    }

    @Test
    void post_orderAlreadyQueued_returnsUnchangedWithoutCallingEzee() {
        service = new EzeeChargePostService(ezeeClient, "Cafe", "Asia/Kolkata");
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
        service = new EzeeChargePostService(ezeeClient, "Cafe", "Asia/Kolkata");

        Map<String, String> roomqueryResponse = new LinkedHashMap<>();
        roomqueryResponse.put("status", "ok");
        roomqueryResponse.put("masterfolio", "8");

        Map<String, String> row1 = new LinkedHashMap<>();
        row1.put("room", "106");
        row1.put("masterfolio", "10");
        Map<String, String> row2 = new LinkedHashMap<>();
        row2.put("room", "106");
        row2.put("masterfolio", "11");

        when(ezeeClient.postRoomQuery(argWithOprn("roomquery")))
                .thenReturn(new RoomQueryResult(roomqueryResponse, List.of(row1, row2)));

        Order result = service.post(sampleOrder(), "106");

        assertEquals("FAILED", result.getChargePostStatus());
        assertTrue(result.getChargePostError().contains("multiple occupants"));
    }

    private LinkedHashMap<String, String> argWithOprn(String oprn) {
        return org.mockito.ArgumentMatchers.argThat(m -> m != null && oprn.equals(m.get("oprn")));
    }
}

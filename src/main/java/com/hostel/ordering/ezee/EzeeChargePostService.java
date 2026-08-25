package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Given an admin-picked eZee room number, resolves the live folio and posts
// the order's charge. Mutates and returns the same Order with chargePost*
// fields set; never throws — every failure path lands in
// chargePostStatus=FAILED so the caller's request never has to handle an
// exception, only inspect the returned Order.
@Service
public class EzeeChargePostService {

    private static final Logger log = LoggerFactory.getLogger(EzeeChargePostService.class);

    private final EzeeClient ezeeClient;
    private final String foodChargeId;

    public EzeeChargePostService(EzeeClient ezeeClient,
            @Value("${ezee.food-charge-id:}") String foodChargeId) {
        this.ezeeClient = ezeeClient;
        this.foodChargeId = foodChargeId;
    }

    public Order post(Order order, String room) {
        if ("QUEUED".equals(order.getChargePostStatus())) {
            log.warn("post() called on order {} that already has a QUEUED chargepost — ignoring", order.getId());
            return order;
        }

        order.setChargePostAt(System.currentTimeMillis());

        if (order.getTotalAmount() == null) {
            return markFailed(order, "Order has no total amount");
        }

        try {
            LinkedHashMap<String, String> roomqueryFields = new LinkedHashMap<>();
            roomqueryFields.put("auth", ezeeClient.getAuthCode());
            roomqueryFields.put("oprn", "roomquery");
            roomqueryFields.put("room", room);
            RoomQueryResult roomqueryResult = ezeeClient.postRoomQuery(roomqueryFields);
            Map<String, String> roomqueryResponse = roomqueryResult.fields();

            if (!"ok".equals(roomqueryResponse.get("status"))) {
                return markFailed(order, roomqueryResponse.getOrDefault("msg", "roomquery failed"));
            }

            List<Map<String, String>> occupants = roomqueryResult.rows().stream()
                    .filter(row -> room.equals(row.get("room")))
                    .toList();

            if (occupants.isEmpty()) {
                return markFailed(order, "No occupant found for room " + room);
            }

            Set<String> folios = occupants.stream()
                    .map(row -> row.get("masterfolio"))
                    .filter(f -> f != null)
                    .collect(Collectors.toSet());
            Set<String> resnos = occupants.stream()
                    .map(row -> row.get("resno"))
                    .filter(r -> r != null)
                    .collect(Collectors.toSet());
            if (folios.size() > 1 || resnos.size() > 1) {
                return markFailed(order, "Room " + room + " has multiple occupants on different folios/reservations — cannot determine which guest to charge");
            }
            if (resnos.isEmpty()) {
                return markFailed(order, "eZee did not return a reservation number for room " + room);
            }
            String folio = folios.iterator().next();
            String resno = resnos.iterator().next();

            String amount = String.format(Locale.US, "%.2f", order.getTotalAmount());
            Map<String, String> response = ezeeClient.postExtraCharge(resno, folio, foodChargeId, amount, "1", buildRemark(order));

            if ("ok".equals(response.get("status"))) {
                order.setChargePostStatus("QUEUED");
                order.setChargePostRoom(room);
                order.setChargePostFolio(folio);
                order.setChargePostError(null);
                log.info("Chargepost posted for order {} via AddExtraCharge: resno={}", order.getId(), resno);
                return order;
            }

            order.setChargePostRoom(room);
            order.setChargePostFolio(folio);
            return markFailed(order, response.getOrDefault("msg", "eZee returned an error"));
        } catch (Exception e) {
            log.error("Chargepost threw for order {}", order.getId(), e);
            return markFailed(order, "Unexpected error: " + e.getMessage());
        }
    }

    private Order markFailed(Order order, String reason) {
        order.setChargePostStatus("FAILED");
        order.setChargePostError(reason);
        order.setChargePostRequestId(null);
        log.warn("Chargepost failed for order {}: {}", order.getId(), reason);
        return order;
    }

    private String buildRemark(Order order) {
        String items = order.getItems().stream()
                .map(OrderItem::getMenuItemName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        String posuser = order.getUpdatedBy() == null ? "system" : order.getUpdatedBy();
        return (items.isEmpty() ? "Cafe order" : items) + " (posted by " + posuser + ")";
    }

    // eZee's Kiosk Connectivity API (AddExtraCharge) has no void/remove
    // counterpart, unlike POS2PMS's voidcharge. Never touches
    // chargePostStatus — the charge is still live in eZee, so the Order must
    // keep saying QUEUED rather than claiming a void that can't happen.
    public Order voidPost(Order order) {
        order.setChargePostError("eZee has no API to void this charge — remove the \"Food Charge\" line for folio "
                + order.getChargePostFolio() + " manually in eZee PMS");
        log.warn("Chargepost cannot be auto-voided for order {}: no void API for AddExtraCharge", order.getId());
        return order;
    }
}

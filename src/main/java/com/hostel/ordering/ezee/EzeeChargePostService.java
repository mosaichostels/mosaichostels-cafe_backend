package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Given an admin-picked eZee room number, resolves the live folio and posts
// the order's charge via AddExtraCharge. Mutates and returns the same Order
// with chargePost* fields set; never throws — every failure path lands in
// chargePostStatus=FAILED so the caller's request never has to handle an
// exception, only inspect the returned Order.
//
// LIMITATION: eZee's Kiosk Connectivity API (AddExtraCharge) has no void or
// removal endpoint. Chargepost voids are manual: staff must remove the charge
// line from the guest's folio in eZee PMS. The Order stays marked QUEUED so
// the admin knows the charge is still live, even though auto-void failed.
@Service
public class EzeeChargePostService {

    private static final Logger log = LoggerFactory.getLogger(EzeeChargePostService.class);

    private final EzeeClient ezeeClient;
    private final String foodChargeId;
    private final String essentialChargeId;

    public EzeeChargePostService(EzeeClient ezeeClient,
            @Value("${ezee.food-charge-id:}") String foodChargeId,
            @Value("${ezee.essential-charge-id:}") String essentialChargeId) {
        this.ezeeClient = ezeeClient;
        this.foodChargeId = foodChargeId;
        this.essentialChargeId = essentialChargeId;
    }

    // Helper class to hold roomquery results
    private static class RoomFolioResult {
        final String folio;
        final String resno;
        final String error;

        RoomFolioResult(String folio, String resno) {
            this.folio = folio;
            this.resno = resno;
            this.error = null;
        }

        RoomFolioResult(String error) {
            this.folio = null;
            this.resno = null;
            this.error = error;
        }

        boolean isSuccess() {
            return error == null;
        }
    }

    private RoomFolioResult queryRoomFolio(String room) {
        try {
            LinkedHashMap<String, String> roomqueryFields = new LinkedHashMap<>();
            roomqueryFields.put("auth", ezeeClient.getAuthCode());
            roomqueryFields.put("oprn", "roomquery");
            roomqueryFields.put("room", room);
            RoomQueryResult roomqueryResult = ezeeClient.postRoomQuery(roomqueryFields);
            Map<String, String> roomqueryResponse = roomqueryResult.fields();

            if (!"ok".equals(roomqueryResponse.get("status"))) {
                return new RoomFolioResult(roomqueryResponse.getOrDefault("msg", "roomquery failed"));
            }

            List<Map<String, String>> occupants = roomqueryResult.rows();
            if (occupants.isEmpty()) {
                return new RoomFolioResult("No occupant found for room " + room);
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
                return new RoomFolioResult("Room " + room + " has multiple occupants on different folios/reservations — cannot determine which guest to charge");
            }
            if (resnos.isEmpty()) {
                return new RoomFolioResult("eZee did not return a reservation number for room " + room);
            }
            return new RoomFolioResult(folios.iterator().next(), resnos.iterator().next());
        } catch (Exception e) {
            return new RoomFolioResult("roomquery exception: " + e.getMessage());
        }
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
            RoomFolioResult folioResult = queryRoomFolio(room);
            if (!folioResult.isSuccess()) {
                return markFailed(order, folioResult.error);
            }
            String folio = folioResult.folio;
            String resno = folioResult.resno;

            order.setChargePostRoom(room);
            order.setChargePostFolio(folio);

            // Guest cart mixes menu items and essentials in one order — each type
            // posts to its own pre-configured eZee extra-charge item. Post each
            // OrderItem individually with its actual quantity to eZee.
            Map<String, List<OrderItem>> itemsByType = order.getItems().stream()
                    .collect(Collectors.groupingBy(item -> "ESSENTIAL".equals(item.getType()) ? "ESSENTIAL" : "MENU"));

            // Ensure at least one item has a positive subtotal before attempting to post
            boolean hasPositiveSubtotal = itemsByType.values().stream()
                    .anyMatch(group -> group.stream()
                            .anyMatch(item -> item.getSubtotal() != null && item.getSubtotal() > 0));
            if (!hasPositiveSubtotal) {
                return markFailed(order, "No items with positive subtotal to charge");
            }

            // Retrying after a partial failure must not re-post an item that
            // already succeeded — AddExtraCharge has no rollback, so doing so
            // would double-charge the guest for that item.
            List<String> postedItems = order.getChargePostedItems() != null
                    ? new ArrayList<>(order.getChargePostedItems())
                    : new ArrayList<>();

            List<String> errors = new ArrayList<>();
            for (Map.Entry<String, List<OrderItem>> typeGroup : itemsByType.entrySet()) {
                String chargeId = "ESSENTIAL".equals(typeGroup.getKey()) ? essentialChargeId : foodChargeId;
                if (chargeId == null || chargeId.isBlank()) {
                    errors.add((("ESSENTIAL".equals(typeGroup.getKey())) ? "Essential" : "Food") + " charge id not configured");
                    continue;
                }

                // Post each item in this type group individually
                for (OrderItem item : typeGroup.getValue()) {
                    String itemId = item.getMenuItemId();

                    // Skip if already posted
                    if (postedItems.contains(itemId)) continue;

                    // Validate item
                    if (item.getSubtotal() == null || item.getSubtotal() <= 0) continue;
                    if (item.getQuantity() == null || item.getQuantity() < 1) {
                        errors.add(item.getMenuItemName() + ": invalid quantity");
                        continue;
                    }

                    String amount = String.format(Locale.US, "%.2f", item.getPrice());
                    String qty = item.getQuantity().toString();
                    String comment = buildCommentForItem(item, order.getUpdatedBy());

                    // Retry logic: if postExtraCharge fails with folio/occupant/room error,
                    // re-query once and retry. Max 1 retry to avoid loops.
                    String currentFolio = folio;
                    String currentResno = resno;
                    Map<String, String> response = ezeeClient.postExtraCharge(currentResno, currentFolio, chargeId, amount, qty, comment);

                    if (!"ok".equals(response.get("status"))) {
                        String errorMsg = response.getOrDefault("msg", "eZee returned an error");
                        if (errorMsg.toLowerCase().contains("occupant") || errorMsg.toLowerCase().contains("folio") || errorMsg.toLowerCase().contains("room")) {
                            log.warn("postExtraCharge failed with folio error for order {} item {}, retrying with fresh roomquery", order.getId(), itemId);
                            RoomFolioResult retryFolioResult = queryRoomFolio(room);
                            if (retryFolioResult.isSuccess()) {
                                currentFolio = retryFolioResult.folio;
                                currentResno = retryFolioResult.resno;
                                order.setChargePostFolio(currentFolio);
                                response = ezeeClient.postExtraCharge(currentResno, currentFolio, chargeId, amount, qty, comment);
                                if ("ok".equals(response.get("status"))) {
                                    postedItems.add(itemId);
                                } else {
                                    errors.add(item.getMenuItemName() + ": " + response.getOrDefault("msg", "eZee returned an error after retry"));
                                }
                            } else {
                                errors.add(item.getMenuItemName() + ": Failed to retry: " + retryFolioResult.error);
                            }
                        } else {
                            errors.add(item.getMenuItemName() + ": " + errorMsg);
                        }
                    } else {
                        postedItems.add(itemId);
                    }
                }
            }
            order.setChargePostedItems(postedItems);

            if (errors.isEmpty()) {
                order.setChargePostStatus("QUEUED");
                order.setChargePostError(null);
                log.info("Chargepost posted for order {} via AddExtraCharge: resno={}", order.getId(), resno);
                return order;
            }

            // ponytail: no cross-call rollback — if item1 posts and item2 then fails,
            // item1's charge is already live in eZee and chargePostedItems above stops
            // a retry from posting it again. Upgrade path: void the succeeded items
            // before marking FAILED, once eZee exposes a void API for AddExtraCharge
            // (it currently doesn't).
            return markFailed(order, String.join("; ", errors));
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

    private String buildCommentForItem(OrderItem item, String updatedBy) {
        String name = item.getMenuItemName() != null && !item.getMenuItemName().isBlank()
                ? item.getMenuItemName()
                : "Item";
        String posuser = updatedBy == null ? "system" : updatedBy;
        return name + " x" + item.getQuantity() + " (posted by " + posuser + ")";
    }

    // eZee's Kiosk Connectivity API (AddExtraCharge) has no void/remove
    // counterpart, unlike POS2PMS's voidcharge. Never touches
    // chargePostStatus — the charge is still live in eZee, so the Order must
    // keep saying QUEUED rather than claiming a void that can't happen.
    public Order voidPost(Order order) {
        String amount = order.getTotalAmount() != null ? String.format("%.2f", order.getTotalAmount()) : "unknown";
        order.setChargePostError("eZee has no API to void this charge. Manual removal required: In eZee PMS, " +
                "find folio " + order.getChargePostFolio() + " and remove the \"Food Charge\" line for amount " + amount + " " +
                "(charge will remain live in guest account until manually removed)");
        log.warn("Chargepost cannot be auto-voided for order {}: no void API for AddExtraCharge", order.getId());
        return order;
    }
}

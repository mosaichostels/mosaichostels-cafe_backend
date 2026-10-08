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

    /**
     * Prefix on a chargePostedItems entry whose AddExtraCharge call got no reply: eZee may or may
     * not have recorded it, and it cannot be voided, so a person must check the folio.
     */
    public static final String UNCONFIRMED_PREFIX = "UNCONFIRMED:";

    /**
     * A post sends no new item after this long. OrderService only treats a claim as abandoned
     * well after this (plus the last in-flight call), so recovery cannot race a live post.
     */
    private long postBudgetMs = 5 * 60 * 1000;

    void setPostBudgetMs(long postBudgetMs) {
        this.postBudgetMs = postBudgetMs;
    }

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

        long startedAt = System.currentTimeMillis();
        order.setChargePostAt(startedAt);

        if (order.getTotalAmount() == null) {
            return markFailed(order, "Order has no total amount");
        }

        // Retrying after a partial failure must not re-post an item that
        // already succeeded — AddExtraCharge has no rollback, so doing so
        // would double-charge the guest for that item.
        List<String> postedItems = order.getChargePostedItems() != null
                ? new ArrayList<>(order.getChargePostedItems())
                : new ArrayList<>();
        // The claim only lets an order carrying UNCONFIRMED entries through when the admin has
        // acknowledged them, so by the time we are here they are cleared to be retried.
        List<String> priorEntries = new ArrayList<>(postedItems);
        boolean priorAttempt = !priorEntries.isEmpty();
        postedItems.removeIf(i -> i.startsWith(UNCONFIRMED_PREFIX));

        try {
            RoomFolioResult folioResult = queryRoomFolio(room);
            if (!folioResult.isSuccess()) {
                return markFailed(order, folioResult.error);
            }
            String folio = folioResult.folio;
            String resno = folioResult.resno;

            // A room number is not a guest: if the occupant changed since the first attempt, the
            // earlier items sit on one folio and the rest would land on a stranger's.
            if (priorAttempt && order.getChargePostFolio() != null
                    && (!folio.equals(order.getChargePostFolio())
                    || (order.getChargePostReservation() != null && !resno.equals(order.getChargePostReservation())))) {
                order.setChargePostedItems(priorEntries);
                return markFailed(order, "Room " + room + " now belongs to a different reservation than the earlier "
                        + "attempt (folio " + order.getChargePostFolio() + "). Part of this charge is already on "
                        + "that folio - settle it manually in eZee.");
            }

            order.setChargePostRoom(room);
            order.setChargePostFolio(folio);
            order.setChargePostReservation(resno);

            // Guest cart mixes menu items and essentials in one order — each type
            // posts to its own pre-configured eZee extra-charge item. Post each
            // OrderItem individually with its actual quantity to eZee.
            // Build a map of item->index before grouping so we can uniquely identify
            // items by position (defect 8: handle duplicate menuItemIds and null ids).
            Map<OrderItem, Integer> itemIndexMap = new LinkedHashMap<>();
            for (int i = 0; i < order.getItems().size(); i++) {
                itemIndexMap.put(order.getItems().get(i), i);
            }

            Map<String, List<OrderItem>> itemsByType = order.getItems().stream()
                    .collect(Collectors.groupingBy(item -> "ESSENTIAL".equals(item.getType()) ? "ESSENTIAL" : "MENU"));

            // Ensure at least one item has a positive subtotal before attempting to post
            boolean hasPositiveSubtotal = itemsByType.values().stream()
                    .anyMatch(group -> group.stream()
                            .anyMatch(item -> item.getSubtotal() != null && item.getSubtotal() > 0));
            if (!hasPositiveSubtotal) {
                return markFailed(order, "No items with positive subtotal to charge");
            }

            List<String> errors = new ArrayList<>();
            boolean unconfirmed = false;
            outer:
            for (Map.Entry<String, List<OrderItem>> typeGroup : itemsByType.entrySet()) {
                String chargeId = "ESSENTIAL".equals(typeGroup.getKey()) ? essentialChargeId : foodChargeId;
                if (chargeId == null || chargeId.isBlank()) {
                    errors.add((("ESSENTIAL".equals(typeGroup.getKey())) ? "Essential" : "Food") + " charge id not configured");
                    continue;
                }

                // Post each item in this type group individually
                for (OrderItem item : typeGroup.getValue()) {
                    // Use positional index for unique dedup key (defect 8: handles duplicate menuItemIds and null ids)
                    Integer itemIndex = itemIndexMap.get(item);
                    String menuItemId = item.getMenuItemId() != null ? item.getMenuItemId() : "unknown";
                    String itemId = menuItemId + "#" + (itemIndex != null ? itemIndex : -1);

                    // Skip if already posted
                    if (postedItems.contains(itemId)) continue;

                    // Validate item
                    if (item.getSubtotal() == null || item.getSubtotal() <= 0) continue;
                    if (item.getQuantity() == null || item.getQuantity() < 1) {
                        errors.add(item.getMenuItemName() + ": invalid quantity");
                        continue;
                    }
                    if (item.getPrice() == null) {
                        errors.add(item.getMenuItemName() + ": invalid price");
                        continue;
                    }

                    if (System.currentTimeMillis() - startedAt > postBudgetMs) {
                        errors.add("Ran out of the post's time budget before every item was sent - post again to continue");
                        break outer;
                    }

                    String amount = String.format(Locale.US, "%.2f", item.getPrice());
                    String qty = item.getQuantity().toString();
                    // Name only: eZee renders the folio line as "<Comment> [Qty N]" from the
                    // Qty field below, so a quantity in the comment prints it twice.
                    String comment = item.getMenuItemName() != null && !item.getMenuItemName().isBlank()
                            ? item.getMenuItemName()
                            : "Item";

                    // Retry logic: if postExtraCharge fails with folio/occupant/room error,
                    // re-query once and retry. Max 1 retry to avoid loops.
                    String currentFolio = folio;
                    String currentResno = resno;
                    Map<String, String> response = sendCharge(currentResno, currentFolio, chargeId, amount, qty, comment);
                    if (response == null) {
                        postedItems.add(UNCONFIRMED_PREFIX + itemId);
                        errors.add(item.getMenuItemName() + ": no reply from eZee - it may already be on the folio");
                        unconfirmed = true;
                        break outer;
                    }

                    if (!"ok".equals(response.get("status"))) {
                        String errorMsg = response.getOrDefault("msg", "eZee returned an error");
                        if (errorMsg.toLowerCase().contains("occupant") || errorMsg.toLowerCase().contains("folio") || errorMsg.toLowerCase().contains("room")) {
                            log.warn("postExtraCharge failed with folio error for order {} item {}, retrying with fresh roomquery", order.getId(), itemId);
                            RoomFolioResult retryFolioResult = queryRoomFolio(room);
                            if (retryFolioResult.isSuccess()
                                    && !postedItems.isEmpty()
                                    && (!retryFolioResult.folio.equals(folio) || !retryFolioResult.resno.equals(resno))) {
                                // Part of the order is already on the first folio; do not split it.
                                errors.add(item.getMenuItemName() + ": the room's reservation changed mid-post - settle manually in eZee");
                            } else if (retryFolioResult.isSuccess()) {
                                currentFolio = retryFolioResult.folio;
                                currentResno = retryFolioResult.resno;
                                order.setChargePostFolio(currentFolio);
                                order.setChargePostReservation(currentResno);
                                response = sendCharge(currentResno, currentFolio, chargeId, amount, qty, comment);
                                if (response == null) {
                                    postedItems.add(UNCONFIRMED_PREFIX + itemId);
                                    errors.add(item.getMenuItemName() + ": no reply from eZee - it may already be on the folio");
                                    unconfirmed = true;
                                    break outer;
                                }
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

            if (unconfirmed) {
                errors.add("Check the guest's folio in eZee before posting again");
            }

            // ponytail: no cross-call rollback — if item1 posts and item2 then fails,
            // item1's charge is already live in eZee and chargePostedItems above stops
            // a retry from posting it again. Upgrade path: void the succeeded items
            // before marking FAILED, once eZee exposes a void API for AddExtraCharge
            // (it currently doesn't).
            return markFailed(order, String.join("; ", errors));
        } catch (Exception e) {
            log.error("Chargepost threw for order {}", order.getId(), e);
            // Never lose an unconfirmed marker: it is what stops a retry without a folio check.
            for (String entry : priorEntries) {
                if (entry.startsWith(UNCONFIRMED_PREFIX) && !postedItems.contains(entry)) postedItems.add(entry);
            }
            order.setChargePostedItems(postedItems);
            return markFailed(order, "Could not confirm the result with eZee (" + e.getMessage()
                    + "). Check the guest's folio in eZee before retrying.");
        }
    }

    // null = the request got no usable reply (timeout, dropped connection): the charge may or may
    // not have been recorded, which is different from eZee saying no.
    private Map<String, String> sendCharge(String resno, String folio, String chargeId,
            String amount, String qty, String comment) {
        try {
            return ezeeClient.postExtraCharge(resno, folio, chargeId, amount, qty, comment);
        } catch (RuntimeException e) {
            // Any failure after the request left (timeout, a reply that cannot be parsed) leaves it
            // unknown whether eZee recorded the charge, so none of them may look like a plain error.
            log.warn("AddExtraCharge outcome unknown: {}", e.toString());
            return null;
        }
    }

    private Order markFailed(Order order, String reason) {
        order.setChargePostStatus("FAILED");
        order.setChargePostError(reason);
        order.setChargePostRequestId(null);
        log.warn("Chargepost failed for order {}: {}", order.getId(), reason);
        return order;
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

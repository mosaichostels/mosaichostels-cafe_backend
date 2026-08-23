package com.hostel.ordering.ezee;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// Given an admin-picked eZee room number, resolves the live folio and posts
// the order's charge. Mutates and returns the same Order with chargePost*
// fields set; never throws — every failure path lands in
// chargePostStatus=FAILED so the caller's request never has to handle an
// exception, only inspect the returned Order.
@Service
public class EzeeChargePostService {

    private static final Logger log = LoggerFactory.getLogger(EzeeChargePostService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EzeeClient ezeeClient;
    private final String outlet;

    public EzeeChargePostService(EzeeClient ezeeClient, @Value("${ezee.outlet:Cafe}") String outlet) {
        this.ezeeClient = ezeeClient;
        this.outlet = outlet;
    }

    public Order post(Order order, String room) {
        order.setChargePostAt(System.currentTimeMillis());

        if (order.getTotalAmount() == null) {
            return markFailed(order, "Order has no total amount");
        }

        try {
            LinkedHashMap<String, String> roomqueryFields = new LinkedHashMap<>();
            roomqueryFields.put("auth", ezeeClient.getAuthCode());
            roomqueryFields.put("oprn", "roomquery");
            roomqueryFields.put("room", room);
            Map<String, String> roomqueryResponse = ezeeClient.post(roomqueryFields);

            if (!"ok".equals(roomqueryResponse.get("status"))) {
                return markFailed(order, roomqueryResponse.getOrDefault("msg", "roomquery failed"));
            }

            String folio = roomqueryResponse.get("masterfolio");

            LinkedHashMap<String, String> chargepostFields = buildChargePostFields(order, room, folio);
            Map<String, String> response = ezeeClient.post(chargepostFields);

            if ("ok".equals(response.get("status"))) {
                order.setChargePostStatus("QUEUED");
                order.setChargePostRequestId(response.get("requestid"));
                order.setChargePostRoom(room);
                order.setChargePostFolio(folio);
                order.setChargePostError(null);
                log.info("Chargepost queued for order {}: requestid={}", order.getId(), response.get("requestid"));
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

    private LinkedHashMap<String, String> buildChargePostFields(Order order, String room, String folio) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String remark = order.getItems().stream()
                .map(OrderItem::getMenuItemName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        String amount = String.format(Locale.US, "%.2f", order.getTotalAmount());

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("auth", ezeeClient.getAuthCode());
        fields.put("oprn", "chargepost");
        fields.put("room", room);
        fields.put("folio", folio);
        fields.put("table", "chargepost");
        fields.put("outlet", outlet);
        fields.put("charge", "Restaurant Charge");
        fields.put("postingdate", today);
        fields.put("trandate", today);
        fields.put("amount", amount);
        fields.put("tax", "0.00");
        fields.put("gross_amount", amount);
        fields.put("voucherno", order.getId());
        fields.put("remark", remark.isEmpty() ? "Cafe order" : remark);
        fields.put("posuser", order.getUpdatedBy() == null ? "system" : order.getUpdatedBy());
        return fields;
    }

    // Reverses a QUEUED chargepost via voidcharge. Never touches
    // chargePostStatus on failure — the charge is still live in eZee, so the
    // Order must keep saying QUEUED rather than claiming a void that didn't
    // happen.
    public Order voidPost(Order order) {
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("auth", ezeeClient.getAuthCode());
            fields.put("oprn", "voidcharge");
            fields.put("requestid", order.getChargePostRequestId());
            Map<String, String> response = ezeeClient.post(fields);

            if ("ok".equals(response.get("status"))) {
                order.setChargePostStatus("VOIDED");
                order.setChargePostError(null);
                log.info("Chargepost voided for order {}: requestid={}", order.getId(), order.getChargePostRequestId());
            } else {
                order.setChargePostError("Void failed: " + response.getOrDefault("msg", "eZee returned an error"));
                log.warn("Chargepost void failed for order {}: {}", order.getId(), order.getChargePostError());
            }
        } catch (Exception e) {
            order.setChargePostError("Void failed: " + e.getMessage());
            log.error("Chargepost void threw for order {}", order.getId(), e);
        }
        return order;
    }
}

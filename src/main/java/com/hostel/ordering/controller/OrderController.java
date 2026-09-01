package com.hostel.ordering.controller;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody Order order) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        Order order = orderService.getOrder(id);
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dormitory,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dateFrom,
            @RequestParam(required = false) Long dateTo,
            @RequestParam(required = false) Long date,
            @RequestParam(required = false, defaultValue = "createdAt_desc") String sort) {
        return ResponseEntity.ok(orderService.getFilteredOrders(
                status, dormitory, search, dateFrom, dateTo, date, sort));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable String id,
            @RequestBody Map<String, String> payload,
            Authentication authentication) {
        String status = payload.get("status");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status parameter cannot be null or empty");
        }
        String updatedBy = authentication != null && authentication.isAuthenticated() ? authentication.getName() : "UNKNOWN";
        Order updated = orderService.updateOrderStatus(id, status, updatedBy);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteOrder(@PathVariable String id) {
        orderService.deleteOrder(id);
        return ResponseEntity.ok("Order deleted successfully");
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dormitory,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dateFrom,
            @RequestParam(required = false) Long dateTo,
            @RequestParam(required = false) Long date,
            @RequestParam(required = false, defaultValue = "false") boolean all,
            @RequestParam(required = false) String confirmToken,
            Authentication authentication) {
        if (!all && status == null && dormitory == null && search == null && dateFrom == null && dateTo == null && date == null) {
            throw new IllegalArgumentException("Must specify at least one filter or set all=true");
        }
        if (all) {
            // Delete-all requires explicit confirmation token for safety
            if (confirmToken == null || confirmToken.isEmpty()) {
                // Return instruction to get token first
                long tokenExpiry = System.currentTimeMillis() + 30000; // 30 second window
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("error", "Confirmation required for delete-all");
                response.put("requiresConfirmation", true);
                response.put("tokenExpiry", tokenExpiry);
                return ResponseEntity.status(400).body(response.toString());
            }

            // Verify token (in production, validate against server-side generated token)
            // This is a simplified check; ideally use a proper token store
            try {
                long tokenTimestamp = Long.parseLong(confirmToken);
                long now = System.currentTimeMillis();
                if (now - tokenTimestamp > 30000 || tokenTimestamp > now) {
                    throw new IllegalArgumentException("Confirmation token expired or invalid");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid confirmation token format");
            }

            String auditedBy = authentication != null && authentication.isAuthenticated() ? authentication.getName() : "UNKNOWN";
            orderService.deleteAllOrders();

            // Add audit trail
            String message = "Admin deleted ALL orders - User: " + auditedBy + ", Timestamp: " + System.currentTimeMillis();
            // auditService.logAction("DELETE_ALL_ORDERS", message);

            return ResponseEntity.ok("All orders deleted successfully (admin: " + auditedBy + ")");
        }
        orderService.deleteFilteredOrders(status, dormitory, search, dateFrom, dateTo, date);
        return ResponseEntity.ok("Filtered orders deleted successfully");
    }

    @PostMapping("/{id}/chargepost")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Order> postCharge(@PathVariable String id,
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        String room = payload.get("room");
        if (room == null || room.isBlank()) {
            throw new IllegalArgumentException("Room parameter cannot be null or empty");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Order cached = (Order) orderService.getIdempotencyResult(idempotencyKey);
            if (cached != null) {
                return ResponseEntity.ok(cached);
            }
        }

        String updatedBy = authentication != null && authentication.isAuthenticated() ? authentication.getName() : "UNKNOWN";
        Order result = orderService.postChargeForOrder(id, room, updatedBy);

        if (result != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            orderService.cacheIdempotencyResult(idempotencyKey, result);
        }

        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/ezee-candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, String>>> searchEzeeCandidates(
            @PathVariable String id,
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(orderService.searchEzeeCandidates(name));
    }
}

package com.hostel.ordering.controller;

import com.hostel.ordering.dto.CreateOrderRequest;
import com.hostel.ordering.model.Order;
import com.hostel.ordering.service.AuditService;
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
    private final AuditService auditService;

    public OrderController(OrderService orderService, AuditService auditService) {
        this.orderService = orderService;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        Order cached = checkIdempotencyCache(idempotencyKey, Order.class);
        if (cached != null) {
            return ResponseEntity.status(HttpStatus.CREATED).body(cached);
        }

        String createdBy = getAuthenticatedUser(authentication);
        Order created = orderService.createOrder(request, createdBy);

        if (created != null) {
            cacheIdempotencyIfPresent(idempotencyKey, created);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        String status = payload.get("status");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status parameter cannot be null or empty");
        }

        Order cached = checkIdempotencyCache(idempotencyKey, Order.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        String updatedBy = getAuthenticatedUser(authentication);
        Order updated = orderService.updateOrderStatus(id, status, updatedBy);

        if (updated != null) {
            cacheIdempotencyIfPresent(idempotencyKey, updated);
        }

        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteOrder(@PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String cached = checkIdempotencyCache(idempotencyKey, String.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        orderService.deleteOrder(id);
        cacheIdempotencyIfPresent(idempotencyKey, "Order deleted successfully");

        return ResponseEntity.ok("Order deleted successfully");
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dormitory,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dateFrom,
            @RequestParam(required = false) Long dateTo,
            @RequestParam(required = false) Long date,
            @RequestParam(required = false, defaultValue = "false") boolean all,
            @RequestParam(required = false) String confirmToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        if (!all && status == null && dormitory == null && search == null && dateFrom == null && dateTo == null && date == null) {
            throw new IllegalArgumentException("Must specify at least one filter or set all=true");
        }

        String cached = checkIdempotencyCache(idempotencyKey, String.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        String result;
        if (all) {
            // Delete-all requires explicit confirmation token for safety
            if (confirmToken == null || confirmToken.isEmpty()) {
                // Return instruction to get token first
                long tokenExpiry = System.currentTimeMillis() + 30000; // 30 second window
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("error", "Confirmation required for delete-all");
                response.put("requiresConfirmation", true);
                response.put("tokenExpiry", tokenExpiry);
                return ResponseEntity.status(400).body(response);
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

            String auditedBy = getAuthenticatedUser(authentication);
            orderService.deleteAllOrders();

            // Add audit trail
            String message = "Admin deleted ALL orders - User: " + auditedBy + ", Timestamp: " + System.currentTimeMillis();
            auditService.logAction("DELETE_ALL_ORDERS", message);

            result = "All orders deleted successfully (admin: " + auditedBy + ")";
        } else {
            String auditedBy = getAuthenticatedUser(authentication);
            orderService.deleteFilteredOrders(status, dormitory, search, dateFrom, dateTo, date);
            String filterDetails = String.format("Deleted filtered orders - User: %s, Filters: status=%s, dormitory=%s, search=%s, dateFrom=%s, dateTo=%s, date=%s",
                    auditedBy, status, dormitory, search, dateFrom, dateTo, date);
            auditService.logAction("ORDERS_FILTERED_DELETED", filterDetails);
            result = "Filtered orders deleted successfully";
        }

        cacheIdempotencyIfPresent(idempotencyKey, result);
        return ResponseEntity.ok(result);
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

        Order cached = checkIdempotencyCache(idempotencyKey, Order.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        String updatedBy = getAuthenticatedUser(authentication);
        Order result = orderService.postChargeForOrder(id, room, updatedBy);

        if (result != null) {
            cacheIdempotencyIfPresent(idempotencyKey, result);
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

    private <T> T checkIdempotencyCache(String idempotencyKey, Class<T> type) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return orderService.getIdempotencyResult(idempotencyKey, type);
        }
        return null;
    }

    private void cacheIdempotencyIfPresent(String idempotencyKey, Object result) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            orderService.cacheIdempotencyResult(idempotencyKey, result);
        }
    }

    private String getAuthenticatedUser(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                ? authentication.getName()
                : "UNKNOWN";
    }
}

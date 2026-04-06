package com.hostel.ordering.controller;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
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
    public ResponseEntity<Order> updateOrderStatus(@PathVariable String id,
                                                    @RequestBody Map<String, String> payload) {
        Order updated = orderService.updateOrderStatus(id, payload.get("status"));
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteOrder(@PathVariable String id) {
        orderService.deleteOrder(id);
        return ResponseEntity.ok("Order deleted successfully");
    }

    @DeleteMapping
    public ResponseEntity<String> deleteOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dormitory,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dateFrom,
            @RequestParam(required = false) Long dateTo,
            @RequestParam(required = false) Long date,
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        if (all) {
            orderService.deleteAllOrders();
            return ResponseEntity.ok("All orders deleted successfully");
        }
        orderService.deleteFilteredOrders(status, dormitory, search, dateFrom, dateTo, date);
        return ResponseEntity.ok("Filtered orders deleted successfully");
    }
}

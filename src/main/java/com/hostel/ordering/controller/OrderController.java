package com.hostel.ordering.controller;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        Order created = orderService.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        Order order = orderService.getOrder(id);
        if (order != null) {
            return ResponseEntity.ok(order);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<Object> getAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dormitory,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dateFrom,
            @RequestParam(required = false) Long dateTo,
            @RequestParam(required = false, defaultValue = "createdAt_desc") String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Object result = orderService.getFilteredOrders(
                status, dormitory, search, dateFrom, dateTo, sort, page, size);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String status = payload.get("status");
        Order updated = orderService.updateOrderStatus(id, status);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
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
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        if (all) {
            orderService.deleteAllOrders();
            return ResponseEntity.ok("All orders deleted successfully");
        } else {
            orderService.deleteFilteredOrders(status, dormitory, search, dateFrom, dateTo);
            return ResponseEntity.ok("Filtered orders deleted successfully");
        }
    }
}

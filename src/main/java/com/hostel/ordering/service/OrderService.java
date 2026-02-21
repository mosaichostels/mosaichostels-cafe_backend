package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    @Lazy
    private FCMNotificationService fcmNotificationService;

    public Order createOrder(Order order) {
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());
        order.setStatus("ORDERED");
        Order saved = orderRepository.save(order);
        // Send push notification to Android app
        fcmNotificationService.sendNewOrderNotification(saved);
        return saved;
    }

    public Order getOrder(String id) {
        Optional<Order> order = orderRepository.findById(id);
        return order.orElse(null);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<Order> getFilteredOrders(String status, String dormitory, String search, Long dateFrom, Long dateTo, String sort) {
        List<Order> orders;

        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean isNumericSearch = hasSearch && search.trim().matches("\\d+");

        if (dateFrom != null && dateTo != null) {
            if (status != null && !status.isEmpty()) {
                orders = orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, dateFrom, dateTo);
            } else {
                orders = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(dateFrom, dateTo);
            }
        } else if (hasSearch) {
            if (isNumericSearch) {
                // Phone number search
                if (status != null && !status.isEmpty()) {
                    orders = orderRepository.findByStatusAndPhoneNumberContainingOrderByCreatedAtDesc(status, search.trim());
                } else {
                    orders = orderRepository.findByPhoneNumberContainingOrderByCreatedAtDesc(search.trim());
                }
            } else {
                // Name search
                if (status != null && !status.isEmpty()) {
                    orders = orderRepository.findByStatusAndBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(status, search.trim());
                } else {
                    orders = orderRepository.findByBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(search.trim());
                }
            }
        } else if (dormitory != null && !dormitory.isEmpty()) {
            if (status != null && !status.isEmpty()) {
                orders = orderRepository.findByStatusAndDormitoryOrderByCreatedAtDesc(status, dormitory);
            } else {
                orders = orderRepository.findByDormitoryOrderByCreatedAtDesc(dormitory);
            }
        } else if (status != null && !status.isEmpty()) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }

        // Apply sorting
        if (sort != null) {
            switch (sort) {
                case "total_asc":
                    orders = orders.stream().sorted((a, b) -> Double.compare(a.getTotalAmount(), b.getTotalAmount())).collect(Collectors.toList());
                    break;
                case "total_desc":
                    orders = orders.stream().sorted((a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount())).collect(Collectors.toList());
                    break;
                case "createdAt_asc":
                    orders = orders.stream().sorted((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt())).collect(Collectors.toList());
                    break;
                default: // createdAt_desc - already sorted by repo
                    break;
            }
        }

        return orders;
    }

    public Order updateOrderStatus(String id, String status) {
        Optional<Order> optionalOrder = orderRepository.findById(id);

        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.setStatus(status);
            order.setUpdatedAt(System.currentTimeMillis());
            return orderRepository.save(order);
        }

        return null;
    }

    public void deleteOrder(String id) {
        orderRepository.deleteById(id);
    }

    public void deleteAllOrders() {
        orderRepository.deleteAll();
    }

    public void deleteFilteredOrders(String status, String dormitory, String search, Long dateFrom, Long dateTo) {
        List<Order> orders = getFilteredOrders(status, dormitory, search, dateFrom, dateTo, null);
        orderRepository.deleteAll(orders);
    }
}

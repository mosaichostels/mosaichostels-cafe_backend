package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.repository.OrderRepository;
import com.hostel.ordering.repository.OrderRepositoryCustom.SearchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final FCMNotificationService fcmNotificationService;
    private final AuditService auditService;
    private final OrderStatusService orderStatusService;

    public OrderService(OrderRepository orderRepository,
                        FCMNotificationService fcmNotificationService,
                        AuditService auditService,
                        OrderStatusService orderStatusService) {
        this.orderRepository = orderRepository;
        this.fcmNotificationService = fcmNotificationService;
        this.auditService = auditService;
        this.orderStatusService = orderStatusService;
    }

    public Order createOrder(Order order) {
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());
        if (order.getCreatedBy() == null || order.getCreatedBy().isEmpty()) {
            order.setCreatedBy("Guest");
        }
        order.setUpdatedBy(order.getCreatedBy());
        if (order.getStatus() == null || order.getStatus().isEmpty()) {
            order.setStatus("ORDERED");
        }
        Order saved = orderRepository.save(order);
        log.info("New order created for {} in {}", saved.getBookingName(), saved.getDormitory());
        fcmNotificationService.sendNewOrderNotification(saved);
        auditService.logAction("ORDER_CREATED", "Created order for " + saved.getBookingName() + " in " + saved.getDormitory());
        return saved;
    }

    public Order getOrder(String id) {
        return orderRepository.findById(id).orElse(null);
    }

    public List<Order> getFilteredOrders(String status, String dormitory, String search,
                                                Long dateFrom, Long dateTo, Long date, String sort) {
        SearchCriteria criteria = new SearchCriteria(status, dormitory, search, dateFrom, dateTo, date);
        List<Order> orders = orderRepository.searchOrders(criteria);

        if (sort != null) {
            Comparator<Order> comparator = switch (sort) {
                case "total_asc" -> Comparator.comparingDouble(Order::getTotalAmount);
                case "total_desc" -> Comparator.comparingDouble(Order::getTotalAmount).reversed();
                case "createdAt_asc" -> Comparator.comparingLong(Order::getCreatedAt);
                case "createdAt_desc" -> Comparator.comparingLong(Order::getCreatedAt).reversed();
                default -> null;
            };
            if (comparator != null) {
                orders = orders.stream().sorted(comparator).toList();
            }
        }

        return orders;
    }

    public Order updateOrderStatus(String id, String status, String updatedBy) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be empty");
        }

        Set<String> validStatuses = orderStatusService.getAllStatuses().stream()
                .map(OrderStatusConfig::getValue)
                .collect(Collectors.toSet());

        if (!validStatuses.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Valid statuses: " + validStatuses);
        }

        return orderRepository.findById(id)
                .map(order -> {
                    String oldStatus = order.getStatus();
                    order.setStatus(status);
                    order.setUpdatedAt(System.currentTimeMillis());
                    if (updatedBy != null) {
                        order.setUpdatedBy(updatedBy);
                    }
                    Order updated = orderRepository.save(order);
                    log.info("Order for {} status updated: {} -> {}", order.getBookingName(), oldStatus, status);
                    auditService.logAction("ORDER_STATUS_UPDATED", "Status updated for " + order.getBookingName() + ": " + oldStatus + " -> " + status);

                    if ("CANCELLED".equalsIgnoreCase(status)) {
                        fcmNotificationService.sendOrderCancelledNotification(updated);
                    }

                    return updated;
                })
                .orElse(null);
    }

    public void deleteOrder(String id) {
        orderRepository.findById(id).ifPresent(order -> {
            orderRepository.delete(order);
            log.info("Order for {} deleted successfully", order.getBookingName());
            auditService.logAction("ORDER_DELETED", "Deleted order for " + order.getBookingName());
        });
    }

    public void deleteAllOrders() {
        orderRepository.deleteAll();
        log.warn("All orders cleared from the system!");
        auditService.logAction("ORDERS_BULK_DELETED", "All orders cleared");
    }

    public void deleteFilteredOrders(String status, String dormitory, String search, Long dateFrom, Long dateTo, Long date) {
        List<Order> orders = getFilteredOrders(status, dormitory, search, dateFrom, dateTo, date, null);
        orderRepository.deleteAll(orders);
    }
}

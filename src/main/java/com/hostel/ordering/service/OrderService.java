package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.PagedResponse;
import com.hostel.ordering.repository.OrderRepository;
import com.hostel.ordering.repository.OrderRepositoryCustom.SearchCriteria;
import com.hostel.ordering.repository.OrderRepositoryCustom.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final FCMNotificationService fcmNotificationService;
    private final AuditService auditService;

    public OrderService(OrderRepository orderRepository,
                        FCMNotificationService fcmNotificationService,
                        AuditService auditService) {
        this.orderRepository = orderRepository;
        this.fcmNotificationService = fcmNotificationService;
        this.auditService = auditService;
    }

    public Order createOrder(Order order) {
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());
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

    /**
     * Returns a paginated response when page/size are provided (web admin).
     * Falls back to full list when pagination params are null (Android app).
     */
    public Object getFilteredOrders(String status, String dormitory, String search,
                                     Long dateFrom, Long dateTo, Long date, String sort,
                                     Integer page, Integer size) {
        if (page != null && size != null) {
            return getFilteredOrdersPaged(status, dormitory, search, dateFrom, dateTo, date, sort, page, size);
        } else {
            return getFilteredOrdersList(status, dormitory, search, dateFrom, dateTo, date, sort);
        }
    }

    private PagedResponse<Order> getFilteredOrdersPaged(String status, String dormitory,
            String search, Long dateFrom, Long dateTo, Long date, String sort, int page, int size) {

        boolean needsInMemorySort = "total_asc".equals(sort) || "total_desc".equals(sort);

        SearchCriteria criteria = new SearchCriteria(status, dormitory, search, dateFrom, dateTo, date, true);
        PageRequest pageable = PageRequest.of(page, needsInMemorySort ? Integer.MAX_VALUE : size);

        SearchResult result = orderRepository.searchOrders(criteria, pageable);
        List<Order> orders = result.orders();

        if (needsInMemorySort) {
            Comparator<Order> comparator = "total_asc".equals(sort)
                    ? Comparator.comparingDouble(Order::getTotalAmount)
                    : Comparator.comparingDouble(Order::getTotalAmount).reversed();

            orders = orders.stream().sorted(comparator).toList();

            int fromIdx = page * size;
            int toIdx = Math.min(fromIdx + size, orders.size());
            long total = orders.size();
            orders = fromIdx >= orders.size() ? List.of() : orders.subList(fromIdx, toIdx);
            return new PagedResponse<>(orders, page, size, total);
        }

        if ("createdAt_asc".equals(sort)) {
            orders = orders.stream()
                    .sorted(Comparator.comparingLong(Order::getCreatedAt))
                    .toList();
        }

        return new PagedResponse<>(orders, page, size, result.totalElements());
    }

    private List<Order> getFilteredOrdersList(String status, String dormitory, String search,
                                               Long dateFrom, Long dateTo, Long date, String sort) {
        SearchCriteria criteria = new SearchCriteria(status, dormitory, search, dateFrom, dateTo, date, false);
        List<Order> orders = orderRepository.searchOrders(criteria, null).orders();

        if (sort != null) {
            Comparator<Order> comparator = switch (sort) {
                case "total_asc" -> Comparator.comparingDouble(Order::getTotalAmount);
                case "total_desc" -> Comparator.comparingDouble(Order::getTotalAmount).reversed();
                case "createdAt_asc" -> Comparator.comparingLong(Order::getCreatedAt);
                default -> null;
            };
            if (comparator != null) {
                orders = orders.stream().sorted(comparator).toList();
            }
        }

        return orders;
    }

    public Order updateOrderStatus(String id, String status) {
        return orderRepository.findById(id)
                .map(order -> {
                    String oldStatus = order.getStatus();
                    order.setStatus(status);
                    order.setUpdatedAt(System.currentTimeMillis());
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
        List<Order> orders = getFilteredOrdersList(status, dormitory, search, dateFrom, dateTo, date, null);
        orderRepository.deleteAll(orders);
    }
}

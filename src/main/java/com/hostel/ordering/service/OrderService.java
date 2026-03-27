package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.PagedResponse;
import com.hostel.ordering.repository.OrderRepository;
import com.hostel.ordering.repository.OrderRepositoryCustom.SearchCriteria;
import com.hostel.ordering.repository.OrderRepositoryCustom.SearchResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final FCMNotificationService fcmNotificationService;

    public OrderService(OrderRepository orderRepository,
                        FCMNotificationService fcmNotificationService) {
        this.orderRepository = orderRepository;
        this.fcmNotificationService = fcmNotificationService;
    }

    public Order createOrder(Order order) {
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());
        order.setStatus("ORDERED");
        Order saved = orderRepository.save(order);
        fcmNotificationService.sendNewOrderNotification(saved);
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
                                     Long dateFrom, Long dateTo, String sort,
                                     Integer page, Integer size) {
        if (page != null && size != null) {
            return getFilteredOrdersPaged(status, dormitory, search, dateFrom, dateTo, sort, page, size);
        } else {
            return getFilteredOrdersList(status, dormitory, search, dateFrom, dateTo, sort);
        }
    }

    private PagedResponse<Order> getFilteredOrdersPaged(String status, String dormitory,
            String search, Long dateFrom, Long dateTo, String sort, int page, int size) {

        boolean needsInMemorySort = "total_asc".equals(sort) || "total_desc".equals(sort);

        SearchCriteria criteria = new SearchCriteria(status, dormitory, search, dateFrom, dateTo, true);
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
                                               Long dateFrom, Long dateTo, String sort) {
        SearchCriteria criteria = new SearchCriteria(status, dormitory, search, dateFrom, dateTo, false);
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
                    order.setStatus(status);
                    order.setUpdatedAt(System.currentTimeMillis());
                    return orderRepository.save(order);
                })
                .orElse(null);
    }

    public void deleteOrder(String id) {
        orderRepository.deleteById(id);
    }

    public void deleteAllOrders() {
        orderRepository.deleteAll();
    }

    public void deleteFilteredOrders(String status, String dormitory, String search, Long dateFrom, Long dateTo) {
        List<Order> orders = getFilteredOrdersList(status, dormitory, search, dateFrom, dateTo, null);
        orderRepository.deleteAll(orders);
    }
}

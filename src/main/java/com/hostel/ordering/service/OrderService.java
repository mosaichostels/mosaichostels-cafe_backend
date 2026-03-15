package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.PagedResponse;
import com.hostel.ordering.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
        fcmNotificationService.sendNewOrderNotification(saved);
        return saved;
    }

    public Order getOrder(String id) {
        Optional<Order> order = orderRepository.findById(id);
        return order.orElse(null);
    }

    /**
     * Returns a paginated response when page/size are provided (web admin).
     * Falls back to full list when pagination params are null (Android app).
     */
    public Object getFilteredOrders(String status, String dormitory, String search,
                                     Long dateFrom, Long dateTo, String sort,
                                     Integer page, Integer size) {


        if (page != null && size != null) {
            return getFilteredOrdersPaged(status, dormitory, search, dateFrom, dateTo, sort, page.intValue(), size.intValue());
        } else {
            return getFilteredOrdersList(status, dormitory, search, dateFrom, dateTo, sort);
        }
    }

    // ── Paginated path (web admin) ────────────────────────────────────────────

    private PagedResponse<Order> getFilteredOrdersPaged(String status, String dormitory,
            String search, Long dateFrom, Long dateTo, String sort, int page, int size) {

        // Sort-by-total needs in-memory sort; force DB sort for createdAt variants
        boolean needsInMemorySort = (sort != null) &&
                ("total_asc".equals(sort) || "total_desc".equals(sort));

        Pageable pageable = PageRequest.of(page, needsInMemorySort ? Integer.MAX_VALUE : size);
        Page<Order> resultPage = queryPaged(status, dormitory, search, dateFrom, dateTo, pageable);

        List<Order> orders = resultPage.getContent();

        if (needsInMemorySort) {
            final String finalSort = sort; // local for safety if needed
            if ("total_asc".equals(finalSort)) {
                orders = orders.stream()
                        .sorted((a, b) -> Double.compare(a.getTotalAmount(), b.getTotalAmount()))
                        .toList();
            } else {
                orders = orders.stream()
                        .sorted((a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()))
                        .toList();
            }
            // Manually slice the page after in-memory sort
            int fromIdx = page * size;
            int toIdx = Math.min(fromIdx + size, orders.size());
            long total = orders.size();
            orders = fromIdx >= orders.size() ? List.of() : orders.subList(fromIdx, toIdx);
            return new PagedResponse<>(orders, page, size, total);
        }

        // createdAt_asc needs reverse — fetch DESC then flip (avoids extra repo method)
        if ("createdAt_asc".equals(sort)) {
            orders = orders.stream()
                    .sorted((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()))
                    .toList();
        }

        return new PagedResponse<>(orders, page, size, resultPage.getTotalElements());
    }

    private Page<Order> queryPaged(String status, String dormitory, String search,
                                    Long dateFrom, Long dateTo, Pageable pageable) {
        String trimmedSearch = (search != null) ? search.trim() : "";
        boolean hasSearch = !trimmedSearch.isEmpty();
        boolean isNumericSearch = hasSearch && trimmedSearch.matches("\\d+");

        if (dateFrom != null && dateTo != null) {
            if (status != null && !status.isEmpty()) {
                return orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, dateFrom, dateTo, pageable);
            }
            return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(dateFrom, dateTo, pageable);
        }

        if (hasSearch) {
            if (isNumericSearch) {
                if (status != null && !status.isEmpty()) {
                    return orderRepository.findByStatusAndPhoneNumberContainingOrderByCreatedAtDesc(status, trimmedSearch, pageable);
                }
                return orderRepository.findByPhoneNumberContainingOrderByCreatedAtDesc(trimmedSearch, pageable);
            } else {
                if (status != null && !status.isEmpty()) {
                    return orderRepository.findByStatusAndBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(status, trimmedSearch, pageable);
                }
                return orderRepository.findByBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(trimmedSearch, pageable);
            }
        }

        if (dormitory != null && !dormitory.isEmpty()) {
            if (status != null && !status.isEmpty()) {
                return orderRepository.findByStatusAndDormitoryOrderByCreatedAtDesc(status, dormitory, pageable);
            }
            return orderRepository.findByDormitoryOrderByCreatedAtDesc(dormitory, pageable);
        }

        if (status != null && !status.isEmpty()) {
            return orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }

        return orderRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    // ── Non-paginated path (Android app / bulk delete) ────────────────────────

    private List<Order> getFilteredOrdersList(String status, String dormitory, String search,
                                               Long dateFrom, Long dateTo, String sort) {
        List<Order> orders = queryList(status, dormitory, search, dateFrom, dateTo);

        if (sort != null) {
            switch (sort) {
                case "total_asc":
                    orders = orders.stream()
                            .sorted((a, b) -> Double.compare(a.getTotalAmount(), b.getTotalAmount()))
                            .toList();
                    break;
                case "total_desc":
                    orders = orders.stream()
                            .sorted((a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()))
                            .toList();
                    break;
                case "createdAt_asc":
                    orders = orders.stream()
                            .sorted((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()))
                            .toList();
                    break;
                default:
                    break;
            }
        }
        return orders;
    }

    private List<Order> queryList(String status, String dormitory, String search,
                                   Long dateFrom, Long dateTo) {
        String trimmedSearch = (search != null) ? search.trim() : "";
        boolean hasSearch = !trimmedSearch.isEmpty();
        boolean isNumericSearch = hasSearch && trimmedSearch.matches("\\d+");

        if (dateFrom != null && dateTo != null) {
            if (status != null && !status.isEmpty()) {
                return orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, dateFrom, dateTo);
            }
            return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(dateFrom, dateTo);
        }

        if (hasSearch) {
            if (isNumericSearch) {
                if (status != null && !status.isEmpty()) {
                    return orderRepository.findByStatusAndPhoneNumberContainingOrderByCreatedAtDesc(status, trimmedSearch);
                }
                return orderRepository.findByPhoneNumberContainingOrderByCreatedAtDesc(trimmedSearch);
            } else {
                if (status != null && !status.isEmpty()) {
                    return orderRepository.findByStatusAndBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(status, trimmedSearch);
                }
                return orderRepository.findByBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(trimmedSearch);
            }
        }

        if (dormitory != null && !dormitory.isEmpty()) {
            if (status != null && !status.isEmpty()) {
                return orderRepository.findByStatusAndDormitoryOrderByCreatedAtDesc(status, dormitory);
            }
            return orderRepository.findByDormitoryOrderByCreatedAtDesc(dormitory);
        }

        if (status != null && !status.isEmpty()) {
            return orderRepository.findByStatusOrderByCreatedAtDesc(status);
        }

        return orderRepository.findAllByOrderByCreatedAtDesc();
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
        List<Order> orders = getFilteredOrdersList(status, dormitory, search, dateFrom, dateTo, null);
        orderRepository.deleteAll(orders);
    }
}

package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.PagedResponse;
import com.hostel.ordering.repository.OrderRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final FCMNotificationService fcmNotificationService;

    public OrderService(OrderRepository orderRepository, @Lazy FCMNotificationService fcmNotificationService) {
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

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public Object getFilteredOrders(
            String status, String dormitory, String search,
            Long dateFrom, Long dateTo, String sort,
            Integer page, Integer limit) {

        Sort mongoSort = buildSort(sort);
        if (page != null && limit != null) {
            Pageable pageable = PageRequest.of(page, limit, mongoSort);
            Page<Order> result = queryPaged(status, dormitory, search, dateFrom, dateTo, pageable);
            return new PagedResponse<>(result.getContent(), page, limit, result.getTotalElements());
        } else {
            return queryList(status, dormitory, search, dateFrom, dateTo, mongoSort);
        }
    }

    private Page<Order> queryPaged(String status, String dormitory, String search,
                                    Long dateFrom, Long dateTo, Pageable pageable) {
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasSearch = search != null && !search.trim().isEmpty();

        if (dateFrom != null && dateTo != null) {
            return hasStatus
                    ? orderRepository.findByStatusAndCreatedAtBetween(status, dateFrom, dateTo, pageable)
                    : orderRepository.findByCreatedAtBetween(dateFrom, dateTo, pageable);
        }
        if (hasSearch && search != null) {
            String trimmedSearch = search.trim();
            boolean isNumeric = trimmedSearch.matches("\\d+");
            if (isNumeric) {
                return hasStatus
                        ? orderRepository.findByStatusAndPhoneNumberContaining(status, trimmedSearch, pageable)
                        : orderRepository.findByPhoneNumberContaining(trimmedSearch, pageable);
            } else {
                return hasStatus
                        ? orderRepository.findByStatusAndBookingNameContainingIgnoreCase(status, trimmedSearch, pageable)
                        : orderRepository.findByBookingNameContainingIgnoreCase(trimmedSearch, pageable);
            }
        }
        if (dormitory != null && !dormitory.isEmpty()) {
            return hasStatus
                    ? orderRepository.findByStatusAndDormitory(status, dormitory, pageable)
                    : orderRepository.findByDormitory(dormitory, pageable);
        }
        if (hasStatus) {
            return orderRepository.findByStatus(status, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    private List<Order> queryList(String status, String dormitory, String search,
                                   Long dateFrom, Long dateTo, Sort mongoSort) {
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasSearch = search != null && !search.trim().isEmpty();

        if (dateFrom != null && dateTo != null) {
            return hasStatus
                    ? orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, dateFrom, dateTo)
                    : orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(dateFrom, dateTo);
        }
        if (hasSearch && search != null) {
            String trimmedSearch = search.trim();
            boolean isNumeric = trimmedSearch.matches("\\d+");
            if (isNumeric) {
                return hasStatus
                        ? orderRepository.findByStatusAndPhoneNumberContainingOrderByCreatedAtDesc(status, trimmedSearch)
                        : orderRepository.findByPhoneNumberContainingOrderByCreatedAtDesc(trimmedSearch);
            } else {
                return hasStatus
                        ? orderRepository.findByStatusAndBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(status, trimmedSearch)
                        : orderRepository.findByBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(trimmedSearch);
            }
        }
        if (dormitory != null && !dormitory.isEmpty()) {
            return hasStatus
                    ? orderRepository.findByStatusAndDormitoryOrderByCreatedAtDesc(status, dormitory)
                    : orderRepository.findByDormitoryOrderByCreatedAtDesc(dormitory);
        }
        if (hasStatus) {
            return orderRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    private Sort buildSort(String sort) {
        if (sort == null) return Sort.by(Sort.Direction.DESC, "createdAt");
        return switch (sort) {
            case "total_asc"    -> Sort.by(Sort.Direction.ASC,  "totalAmount");
            case "total_desc"   -> Sort.by(Sort.Direction.DESC, "totalAmount");
            case "createdAt_asc"-> Sort.by(Sort.Direction.ASC,  "createdAt");
            default             -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    public Order updateOrderStatus(String id, String status) {
        return orderRepository.findById(id).map(order -> {
            order.setStatus(status);
            order.setUpdatedAt(System.currentTimeMillis());
            return orderRepository.save(order);
        }).orElse(null);
    }

    public void deleteOrder(String id) {
        orderRepository.deleteById(id);
    }

    public void deleteAllOrders() {
        orderRepository.deleteAll();
    }

    public void deleteFilteredOrders(String status, String dormitory, String search,
                                      Long dateFrom, Long dateTo) {
        List<Order> orders = queryList(status, dormitory, search, dateFrom, dateTo,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        orderRepository.deleteAll(orders);
    }
}

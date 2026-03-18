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

        boolean needsInMemorySort = (sort != null) &&
                ("total_asc".equals(sort) || "total_desc".equals(sort));

        Pageable pageable = PageRequest.of(page, needsInMemorySort ? Integer.MAX_VALUE : size);
        
        QueryContext ctx = new QueryContext();
        ctx.status = status;
        ctx.dormitory = dormitory;
        ctx.search = search;
        ctx.dateFrom = dateFrom;
        ctx.dateTo = dateTo;

        Page<Order> resultPage = executeQuery(ctx, new QueryExecutor<Page<Order>>() {
            @Override
            public Page<Order> byStatusAndDateRange(String s, Long df, Long dt) {
                return orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(s, df, dt, pageable);
            }
            @Override
            public Page<Order> byDateRange(Long df, Long dt) {
                return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(df, dt, pageable);
            }
            @Override
            public Page<Order> byStatusAndPhone(String s, String p) {
                return orderRepository.findByStatusAndPhoneNumberContainingOrderByCreatedAtDesc(s, p, pageable);
            }
            @Override
            public Page<Order> byPhone(String p) {
                return orderRepository.findByPhoneNumberContainingOrderByCreatedAtDesc(p, pageable);
            }
            @Override
            public Page<Order> byStatusAndName(String s, String n) {
                return orderRepository.findByStatusAndBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(s, n, pageable);
            }
            @Override
            public Page<Order> byName(String n) {
                return orderRepository.findByBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(n, pageable);
            }
            @Override
            public Page<Order> byStatusAndDorm(String s, String d) {
                return orderRepository.findByStatusAndDormitoryOrderByCreatedAtDesc(s, d, pageable);
            }
            @Override
            public Page<Order> byDorm(String d) {
                return orderRepository.findByDormitoryOrderByCreatedAtDesc(d, pageable);
            }
            @Override
            public Page<Order> byStatus(String s) {
                return orderRepository.findByStatusOrderByCreatedAtDesc(s, pageable);
            }
            @Override
            public Page<Order> all() {
                return orderRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        });

        List<Order> orders = resultPage.getContent();

        if (needsInMemorySort) {
            final String finalSort = sort;
            if ("total_asc".equals(finalSort)) {
                orders = orders.stream()
                        .sorted((a, b) -> Double.compare(a.getTotalAmount(), b.getTotalAmount()))
                        .toList();
            } else {
                orders = orders.stream()
                        .sorted((a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()))
                        .toList();
            }
            int fromIdx = page * size;
            int toIdx = Math.min(fromIdx + size, orders.size());
            long total = orders.size();
            orders = fromIdx >= orders.size() ? List.of() : orders.subList(fromIdx, toIdx);
            return new PagedResponse<>(orders, page, size, total);
        }

        if ("createdAt_asc".equals(sort)) {
            orders = orders.stream()
                    .sorted((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()))
                    .toList();
        }

        return new PagedResponse<>(orders, page, size, resultPage.getTotalElements());
    }

    private <T> T executeQuery(QueryContext ctx, QueryExecutor<T> executor) {
        String trimmedSearch = (ctx.search != null) ? ctx.search.trim() : "";
        boolean hasSearch = !trimmedSearch.isEmpty();
        boolean isNumericSearch = hasSearch && trimmedSearch.matches("\\d+");

        if (ctx.dateFrom != null && ctx.dateTo != null) {
            if (ctx.hasStatus()) {
                return executor.byStatusAndDateRange(ctx.status, ctx.dateFrom, ctx.dateTo);
            }
            return executor.byDateRange(ctx.dateFrom, ctx.dateTo);
        }

        if (hasSearch) {
            if (isNumericSearch) {
                if (ctx.hasStatus()) {
                    return executor.byStatusAndPhone(ctx.status, trimmedSearch);
                }
                return executor.byPhone(trimmedSearch);
            } else {
                if (ctx.hasStatus()) {
                    return executor.byStatusAndName(ctx.status, trimmedSearch);
                }
                return executor.byName(trimmedSearch);
            }
        }

        if (ctx.hasDormitory()) {
            if (ctx.hasStatus()) {
                return executor.byStatusAndDorm(ctx.status, ctx.dormitory);
            }
            return executor.byDorm(ctx.dormitory);
        }

        if (ctx.hasStatus()) {
            return executor.byStatus(ctx.status);
        }

        return executor.all();
    }

    private interface QueryExecutor<T> {
        T byStatusAndDateRange(String status, Long dateFrom, Long dateTo);
        T byDateRange(Long dateFrom, Long dateTo);
        T byStatusAndPhone(String status, String phone);
        T byPhone(String phone);
        T byStatusAndName(String status, String name);
        T byName(String name);
        T byStatusAndDorm(String status, String dorm);
        T byDorm(String dorm);
        T byStatus(String status);
        T all();
    }

    private static class QueryContext {
        String status;
        String dormitory;
        String search;
        Long dateFrom;
        Long dateTo;

        boolean hasStatus() { return status != null && !status.isEmpty(); }
        boolean hasDormitory() { return dormitory != null && !dormitory.isEmpty(); }
    }

    // ── Non-paginated path (Android app / bulk delete) ────────────────────────

    private List<Order> getFilteredOrdersList(String status, String dormitory, String search,
                                               Long dateFrom, Long dateTo, String sort) {
        QueryContext ctx = new QueryContext();
        ctx.status = status;
        ctx.dormitory = dormitory;
        ctx.search = search;
        ctx.dateFrom = dateFrom;
        ctx.dateTo = dateTo;

        List<Order> orders = executeQuery(ctx, new QueryExecutor<List<Order>>() {
            @Override
            public List<Order> byStatusAndDateRange(String s, Long df, Long dt) {
                return orderRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(s, df, dt);
            }
            @Override
            public List<Order> byDateRange(Long df, Long dt) {
                return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(df, dt);
            }
            @Override
            public List<Order> byStatusAndPhone(String s, String p) {
                return orderRepository.findByStatusAndPhoneNumberContainingOrderByCreatedAtDesc(s, p);
            }
            @Override
            public List<Order> byPhone(String p) {
                return orderRepository.findByPhoneNumberContainingOrderByCreatedAtDesc(p);
            }
            @Override
            public List<Order> byStatusAndName(String s, String n) {
                return orderRepository.findByStatusAndBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(s, n);
            }
            @Override
            public List<Order> byName(String n) {
                return orderRepository.findByBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(n);
            }
            @Override
            public List<Order> byStatusAndDorm(String s, String d) {
                return orderRepository.findByStatusAndDormitoryOrderByCreatedAtDesc(s, d);
            }
            @Override
            public List<Order> byDorm(String d) {
                return orderRepository.findByDormitoryOrderByCreatedAtDesc(d);
            }
            @Override
            public List<Order> byStatus(String s) {
                return orderRepository.findByStatusOrderByCreatedAtDesc(s);
            }
            @Override
            public List<Order> all() {
                return orderRepository.findAllByOrderByCreatedAtDesc();
            }
        });

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

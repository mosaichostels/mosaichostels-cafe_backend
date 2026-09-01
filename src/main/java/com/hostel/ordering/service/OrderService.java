package com.hostel.ordering.service;

import com.hostel.ordering.model.Order;
import com.hostel.ordering.model.OrderItem;
import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.repository.MenuItemRepository;
import com.hostel.ordering.repository.OrderRepository;
import com.hostel.ordering.repository.OrderRepositoryCustom.SearchCriteria;
import com.hostel.ordering.repository.OtherEssentialRepository;
import com.hostel.ordering.ezee.EzeeChargePostService;
import com.hostel.ordering.ezee.EzeeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int MAX_ITEMS = 50;
    private static final int MAX_QUANTITY = 50;

    private final OrderRepository orderRepository;
    private final FCMNotificationService fcmNotificationService;
    private final AuditService auditService;
    private final OrderStatusService orderStatusService;
    private final MenuItemRepository menuItemRepository;
    private final OtherEssentialRepository otherEssentialRepository;
    private final EzeeChargePostService ezeeChargePostService;
    private final EzeeClient ezeeClient;
    private final com.hostel.ordering.repository.UserRepository userRepository;
    private final IdempotencyService idempotencyService;

    public OrderService(OrderRepository orderRepository,
                        FCMNotificationService fcmNotificationService,
                        AuditService auditService,
                        OrderStatusService orderStatusService,
                        MenuItemRepository menuItemRepository,
                        OtherEssentialRepository otherEssentialRepository,
                        EzeeChargePostService ezeeChargePostService,
                        EzeeClient ezeeClient,
                        com.hostel.ordering.repository.UserRepository userRepository,
                        IdempotencyService idempotencyService) {
        this.orderRepository = orderRepository;
        this.fcmNotificationService = fcmNotificationService;
        this.auditService = auditService;
        this.orderStatusService = orderStatusService;
        this.menuItemRepository = menuItemRepository;
        this.otherEssentialRepository = otherEssentialRepository;
        this.ezeeChargePostService = ezeeChargePostService;
        this.ezeeClient = ezeeClient;
        this.userRepository = userRepository;
        this.idempotencyService = idempotencyService;
    }

    public Order createOrder(Order order) {
        repriceOrder(order);
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

    // Prices come from the database, never from the client — prevents total tampering.
    void repriceOrder(Order order) {
        if (order.getItems().size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Too many items in order (max " + MAX_ITEMS + ")");
        }
        double total = 0;
        for (OrderItem item : order.getItems()) {
            if (item.getMenuItemId() == null || item.getMenuItemId().isBlank()) {
                throw new IllegalArgumentException("Item id is required");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > MAX_QUANTITY) {
                throw new IllegalArgumentException("Invalid quantity for item: " + item.getMenuItemName());
            }
            Double price = menuItemRepository.findById(item.getMenuItemId())
                    .filter(m -> !m.isDeleted())
                    .map(m -> { item.setType("MENU"); return m.getPrice(); })
                    .orElseGet(() -> otherEssentialRepository.findById(item.getMenuItemId())
                            .filter(e -> !e.isDeleted())
                            .map(e -> { item.setType("ESSENTIAL"); return e.getPrice(); })
                            .orElse(null));
            if (price == null) {
                throw new IllegalArgumentException("Unknown item: " + item.getMenuItemId());
            }
            item.setPrice(price);
            item.setSubtotal(price * item.getQuantity());
            total += item.getSubtotal();
        }
        order.setTotalAmount(total);
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
                        if ("QUEUED".equals(updated.getChargePostStatus())) {
                            Order voided = ezeeChargePostService.voidPost(updated);
                            updated = orderRepository.save(voided);
                            if (!"VOIDED".equals(voided.getChargePostStatus())) {
                                auditService.logAction("ORDER_CHARGEPOST_VOID_FAILED",
                                        "Failed to void eZee charge for " + updated.getBookingName() + ": " + voided.getChargePostError());
                            }
                        }
                    } else if ("DELIVERED".equalsIgnoreCase(status)) {
                        sendDeliveredNotificationToAdmins(updated);
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

    private void sendDeliveredNotificationToAdmins(Order order) {
        try {
            java.util.List<com.hostel.ordering.model.User> admins = userRepository.findByRolesContaining("ROLE_ADMIN");
            for (com.hostel.ordering.model.User admin : admins) {
                String fcmToken = admin.getFcmToken();
                if (fcmToken != null && !fcmToken.isBlank()) {
                    java.util.Map<String, String> data = new java.util.HashMap<>();
                    data.put("action", "postCharge");
                    data.put("orderId", order.getId() != null ? order.getId() : "");
                    fcmNotificationService.sendNotificationToToken(
                            fcmToken,
                            "Order Ready to Post",
                            "Order for " + order.getBookingName() + " marked delivered - post charge now?",
                            data
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error sending delivered notification to admins: {}", e.getMessage());
        }
    }

    public void deleteAllOrders() {
        orderRepository.deleteAll();
        log.warn("All orders cleared from the system!");
        auditService.logAction("ORDERS_BULK_DELETED", "All orders cleared");
    }

    public void deleteFilteredOrders(String status, String dormitory, String search, Long dateFrom, Long dateTo, Long date) {
        List<Order> orders = getFilteredOrders(status, dormitory, search, dateFrom, dateTo, date, null);
        orderRepository.deleteAll(orders);
        auditService.logAction("ORDERS_FILTERED_DELETED", "Deleted " + orders.size() + " filtered orders");
    }

    @Transactional
    public Order postChargeForOrder(String orderId, String room, String updatedBy) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return null;
        }

        if ("QUEUED".equals(order.getChargePostStatus())) {
            log.warn("Chargepost already queued for order {}, ignoring duplicate post request", orderId);
            return order;
        }

        if (updatedBy != null) {
            order.setUpdatedBy(updatedBy);
        }

        Order result;
        try {
            result = ezeeChargePostService.post(order, room);
        } catch (OptimisticLockingFailureException e) {
            log.warn("Chargepost failed for order {} due to concurrent update", orderId);
            // On optimistic lock failure, reload and fail gracefully
            order = orderRepository.findById(orderId).orElse(order);
            order.setChargePostStatus("FAILED");
            order.setChargePostError("Order was updated concurrently; please retry");
            result = order;
        } catch (Exception e) {
            log.error("Chargepost threw exception for order {}, clearing partial state", orderId, e);
            // On exception, clear any partial state and mark as failed for rollback
            order.setChargePostedItems(new ArrayList<>());
            order.setChargePostStatus("FAILED");
            order.setChargePostError("Chargepost exception: " + e.getMessage());
            result = order;
        }

        // Combine status changes before save for atomicity
        if ("QUEUED".equals(result.getChargePostStatus())) {
            result.setStatus("CHECKED");
            result.setUpdatedAt(System.currentTimeMillis());
        }

        Order saved = orderRepository.save(result);

        if ("QUEUED".equals(saved.getChargePostStatus())) {
            log.info("Order for {} posted to eZee and marked CHECKED", saved.getBookingName());
            auditService.logAction("ORDER_CHECKED", "Order for " + saved.getBookingName() + " posted to eZee room " + room + " and marked CHECKED");
        } else {
            log.warn("Chargepost failed for order {}, status unchanged: {}", saved.getId(), saved.getChargePostError());
            auditService.logAction("ORDER_CHARGEPOST_FAILED", "Chargepost failed for " + saved.getBookingName() + ": " + saved.getChargePostError());
        }

        return saved;
    }

    public List<Map<String, String>> searchEzeeCandidates(String name) {
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("auth", ezeeClient.getAuthCode());
            fields.put("oprn", "roomlist");
            List<Map<String, String>> rows = ezeeClient.postForRoomRows(fields);
            log.info("eZee roomlist returned {} rows for search name={}", rows.size(), name);

            if (name == null || name.isBlank()) {
                return rows;
            }
            String needle = name.toLowerCase();
            return rows.stream()
                    .filter(row -> row.get("guestname") != null && row.get("guestname").toLowerCase().contains(needle))
                    .toList();
        } catch (IllegalStateException e) {
            log.warn("eZee search failed for name={}: {}", name, e.getMessage());
            return List.of();
        }
    }

    public Object getIdempotencyResult(String idempotencyKey) {
        return idempotencyService.getIfPresent(idempotencyKey);
    }

    public void cacheIdempotencyResult(String idempotencyKey, Object result) {
        idempotencyService.put(idempotencyKey, result);
    }

    public void logChargePost(Order order, String action) {
        String details = "Order: " + order.getId() + ", Status: " + order.getChargePostStatus()
                       + ", Room: " + order.getChargePostRoom() + ", Folio: " + order.getChargePostFolio();
        if (order.getChargePostError() != null) {
            details += ", Error: " + order.getChargePostError();
        }
        log.info("CHARGEPOST_" + action.toUpperCase() + " - " + details);
    }
}

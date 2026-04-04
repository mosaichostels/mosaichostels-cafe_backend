package com.hostel.ordering.service;

import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.repository.OrderStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class OrderStatusService {

    private final OrderStatusRepository repository;
    private final AuditService auditService;

    public OrderStatusService(OrderStatusRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<OrderStatusConfig> getAllStatuses() {
        return repository.findAll();
    }

    public OrderStatusConfig addStatus(OrderStatusConfig status) {
        OrderStatusConfig saved = repository.save(status);
        log.info("New order status added: {}", saved.getLabel());
        auditService.logAction("STATUS_ADDED", "Added new order status: " + saved.getLabel() + " (" + saved.getValue() + ")");
        return saved;
    }


    public void deleteStatus(String id) {
        repository.findById(id).ifPresent(s -> {
            if (!s.isLocked()) {
                repository.deleteById(id);
                log.info("Order status {} deleted successfully", s.getLabel());
                auditService.logAction("STATUS_DELETED", "Deleted order status: " + s.getLabel());
            } else {
                log.warn("Attempted to delete locked status: {}", s.getLabel());
            }
        });
    }

}

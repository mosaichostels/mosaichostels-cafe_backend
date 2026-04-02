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
        log.info("Status added: {}", saved.getLabel());
        auditService.logAction("STATUS_ADDED", "Label: " + saved.getLabel() + ", Value: " + saved.getValue());
        return saved;
    }


    public void deleteStatus(String id) {
        repository.findById(id).ifPresent(s -> {
            if (!s.isLocked()) {
                repository.deleteById(id);
                log.warn("Status deleted: {}", s.getLabel());
                auditService.logAction("STATUS_DELETED", "Label: " + s.getLabel() + ", ID: " + id);
            } else {
                log.warn("Attempted to delete locked status: {}", s.getLabel());
            }
        });
    }

}

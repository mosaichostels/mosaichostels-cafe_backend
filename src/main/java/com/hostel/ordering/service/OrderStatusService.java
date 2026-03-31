package com.hostel.ordering.service;

import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.repository.OrderStatusRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderStatusService {

    private final OrderStatusRepository repository;

    public OrderStatusService(OrderStatusRepository repository) {
        this.repository = repository;
    }

    public List<OrderStatusConfig> getAllStatuses() {
        return repository.findAll();
    }

    public OrderStatusConfig addStatus(OrderStatusConfig status) {
        return repository.save(status);
    }

    public void deleteStatus(String id) {
        repository.findById(id).ifPresent(s -> {
            if (!s.isLocked()) {
                repository.deleteById(id);
            }
        });
    }

    public long count() {
        return repository.count();
    }
}

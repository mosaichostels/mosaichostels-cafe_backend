package com.hostel.ordering.service;

import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.repository.DormitoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DormitoryService {

    private final DormitoryRepository repository;
    private final AuditService auditService;

    public DormitoryService(DormitoryRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<Dormitory> getAllDormitories() {
        return repository.findAll();
    }

    public Dormitory addDormitory(Dormitory dormitory) {
        Dormitory saved = repository.save(dormitory);
        log.info("New dormitory added: {}", saved.getName());
        auditService.logAction("DORMITORY_ADDED", "Added dormitory: " + saved.getName());
        return saved;
    }


    public Dormitory updateDormitory(String id, Dormitory dormitory) {
        return repository.findById(id)
                .map(existing -> {
                    String oldName = existing.getName();
                    if (dormitory.getName() != null) existing.setName(dormitory.getName());
                    Dormitory updated = repository.save(existing);
                    log.info("Dormitory updated: {} -> {}", oldName, updated.getName());
                    auditService.logAction("DORMITORY_UPDATED", "Updated dormitory: " + oldName + " -> " + updated.getName());
                    return updated;
                })
                .orElse(null);
    }

    public void deleteDormitory(String id) {
        repository.findById(id).ifPresent(dorm -> {
            repository.delete(dorm);
            log.info("Dormitory {} deleted successfully", dorm.getName());
            auditService.logAction("DORMITORY_DELETED", "Deleted dormitory: " + dorm.getName());
        });
    }
    
}

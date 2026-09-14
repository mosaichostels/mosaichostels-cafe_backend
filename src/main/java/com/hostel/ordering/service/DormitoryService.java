package com.hostel.ordering.service;

import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.repository.DormitoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class DormitoryService {
    private static final Logger log = LoggerFactory.getLogger(DormitoryService.class);

    private final DormitoryRepository repository;
    private final AuditService auditService;

    public DormitoryService(DormitoryRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Ordered by showOrder. /config hands the names out in exactly this sequence, so the
     * arrangement the admin sets here is the one a guest gets in the checkout picker.
     */
    public List<Dormitory> getAllDormitories() {
        List<Dormitory> all = repository.findAllByOrderByShowOrderAsc();
        // Rows written before showOrder existed all carry 0, and nothing can be moved relative
        // to a value every row shares. Number them once, on the first read after the upgrade.
        if (all.stream().anyMatch(d -> d.getShowOrder() <= 0)) {
            renumber(all);
        }
        return all;
    }

    @Transactional
    public Dormitory addDormitory(Dormitory dormitory) {
        int requested = dormitory.getShowOrder();
        Dormitory saved = repository.save(dormitory);
        // placeAt clamps, so MAX_VALUE means "no position asked for - put it last".
        placeAt(saved, requested > 0 ? requested : Integer.MAX_VALUE);
        log.info("New dormitory added: {} at order {}", saved.getName(), saved.getShowOrder());
        auditService.logAction("DORMITORY_ADDED",
                "Added dormitory: " + saved.getName() + " at order " + saved.getShowOrder());
        return saved;
    }

    @Transactional
    public Dormitory updateDormitory(String id, Dormitory dormitory) {
        return repository.findById(id)
                .map(existing -> {
                    String oldName = existing.getName();
                    int oldOrder = existing.getShowOrder();
                    int newOrder = dormitory.getShowOrder();

                    if (dormitory.getName() != null) existing.setName(dormitory.getName());
                    Dormitory updated = repository.save(existing);

                    if (newOrder > 0 && newOrder != oldOrder) {
                        placeAt(updated, newOrder);
                    }

                    log.info("Dormitory updated: {} -> {}, order {} -> {}",
                            oldName, updated.getName(), oldOrder, updated.getShowOrder());
                    auditService.logAction("DORMITORY_UPDATED",
                            "Updated dormitory: " + oldName + " -> " + updated.getName()
                                    + " at order " + updated.getShowOrder());
                    return updated;
                })
                .orElse(null);
    }

    @Transactional
    public void deleteDormitory(String id) {
        repository.findById(id).ifPresent(dorm -> {
            repository.delete(dorm);
            renumber(repository.findAllByOrderByShowOrderAsc());
            log.info("Dormitory {} deleted successfully", dorm.getName());
            auditService.logAction("DORMITORY_DELETED", "Deleted dormitory: " + dorm.getName());
        });
    }

    /**
     * Puts one dormitory at {@code position} among the others and renumbers the lot. Moving
     * within a list rather than incrementing a range of orders makes a swap a single write,
     * and leaves no way for two rows to end up sharing an order whatever the caller asks for.
     */
    private void placeAt(Dormitory moved, int position) {
        List<Dormitory> ordered = new ArrayList<>(repository.findAllByOrderByShowOrderAsc());
        ordered.removeIf(d -> moved.getId() != null && moved.getId().equals(d.getId()));
        int index = Math.max(0, Math.min(position - 1, ordered.size()));
        ordered.add(index, moved);
        renumber(ordered);
    }

    /** Assigns a contiguous 1..n in list order, writing back only the rows that moved. */
    private void renumber(List<Dormitory> ordered) {
        List<Dormitory> changed = new ArrayList<>();
        int next = 1;
        for (Dormitory dorm : ordered) {
            if (dorm.getShowOrder() != next) {
                dorm.setShowOrder(next);
                changed.add(dorm);
            }
            next++;
        }
        if (!changed.isEmpty()) {
            repository.saveAll(changed);
        }
    }
}

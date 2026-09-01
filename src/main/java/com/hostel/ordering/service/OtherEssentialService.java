package com.hostel.ordering.service;

import com.hostel.ordering.model.OtherEssential;
import com.hostel.ordering.repository.OtherEssentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OtherEssentialService {
    private static final Logger log = LoggerFactory.getLogger(OtherEssentialService.class);

    private final OtherEssentialRepository otherEssentialRepository;
    private final AuditService auditService;

    public OtherEssentialService(OtherEssentialRepository otherEssentialRepository, AuditService auditService) {
        this.otherEssentialRepository = otherEssentialRepository;
        this.auditService = auditService;
    }

    public OtherEssential createOtherEssential(OtherEssential otherEssential) {
        OtherEssential saved = otherEssentialRepository.save(otherEssential);
        log.info("New essential item created: {}", saved.getName());
        auditService.logAction("ESSENTIAL_CREATED", "Created essential item: " + saved.getName() + " with price " + saved.getPrice());
        return saved;
    }

    public OtherEssential getOtherEssential(String id) {
        return otherEssentialRepository.findById(id).orElse(null);
    }

    public List<OtherEssential> getAllOtherEssentials() {
        return otherEssentialRepository.findAll();
    }

    public List<OtherEssential> getAvailableOtherEssentials(String category, String sort) {
        List<OtherEssential> items = (category != null && !category.isBlank())
                ? otherEssentialRepository.findByAvailableTrueAndCategoryOrderByNameAsc(category)
                : otherEssentialRepository.findByAvailableTrueOrderByNameAsc();

        String finalSort = (sort == null || sort.isBlank()) ? "price_asc" : sort;

        java.util.Comparator<OtherEssential> comparator = switch (finalSort) {
            case "price_asc" -> java.util.Comparator.comparingDouble(OtherEssential::getPrice);
            case "price_desc" -> java.util.Comparator.comparingDouble(OtherEssential::getPrice).reversed();
            case "name_asc" -> java.util.Comparator.comparing(OtherEssential::getName);
            default -> null;
        };

        if (comparator != null) {
            return items.stream().sorted(comparator).toList();
        }

        return items;
    }

    public List<OtherEssential> searchOtherEssentials(String query, boolean availableOnly) {
        if (availableOnly) {
            return otherEssentialRepository.findByNameContainingIgnoreCaseAndAvailableTrueOrderByNameAsc(query);
        }
        return otherEssentialRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
    }

    public OtherEssential updateOtherEssential(String id, OtherEssential otherEssential) {
        return otherEssentialRepository.findById(id)
                .map(existing -> {
                    String oldName = existing.getName();
                    if (otherEssential.getName() != null) existing.setName(otherEssential.getName());
                    if (otherEssential.getDescription() != null) existing.setDescription(otherEssential.getDescription());
                    if (otherEssential.getPrice() != null) existing.setPrice(otherEssential.getPrice());
                    if (otherEssential.getCategory() != null) existing.setCategory(otherEssential.getCategory());
                    if (otherEssential.getAvailable() != null) existing.setAvailable(otherEssential.getAvailable());
                    OtherEssential updated = otherEssentialRepository.save(existing);
                    log.info("Essential item updated: {} -> {}", oldName, updated.getName());
                    auditService.logAction("ESSENTIAL_UPDATED", "Updated essential item: " + oldName + " -> " + updated.getName());
                    return updated;
                })
                .orElse(null);
    }

    public void deleteOtherEssential(String id) {
        otherEssentialRepository.findById(id).ifPresent(item -> {
            item.setDeleted(true);
            otherEssentialRepository.save(item);
            log.info("Essential item {} marked as deleted", item.getName());
            auditService.logAction("ESSENTIAL_DELETED", "Deleted essential item: " + item.getName());
        });
    }
}

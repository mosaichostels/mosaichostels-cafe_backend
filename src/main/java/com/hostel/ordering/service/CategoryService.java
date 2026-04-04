package com.hostel.ordering.service;

import com.hostel.ordering.model.Category;
import com.hostel.ordering.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderByShowOrderAsc();
    }

    public Category createCategory(Category category) {
        Category saved = categoryRepository.save(category);
        log.info("New category created: {}", saved.getName());
        auditService.logAction("CATEGORY_CREATED", "Created category: " + saved.getName());
        return saved;
    }


    public Category updateCategory(String id, Category category) {
        return categoryRepository.findById(id)
                .map(existing -> {
                    String oldName = existing.getName();
                    if (category.getName() != null) existing.setName(category.getName());
                    if (category.getShowOrder() != 0) existing.setShowOrder(category.getShowOrder());
                    Category updated = categoryRepository.save(existing);
                    log.info("Category updated: {} -> {}", oldName, updated.getName());
                    auditService.logAction("CATEGORY_UPDATED", "Updated category: " + oldName + " -> " + updated.getName());
                    return updated;
                })
                .orElse(null);
    }

    public void deleteCategory(String id) {
        categoryRepository.findById(id).ifPresent(category -> {
            categoryRepository.delete(category);
            log.info("Category {} deleted successfully", category.getName());
            auditService.logAction("CATEGORY_DELETED", "Deleted category: " + category.getName());
        });
    }

}

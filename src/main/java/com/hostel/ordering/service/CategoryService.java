package com.hostel.ordering.service;

import com.hostel.ordering.model.Category;
import com.hostel.ordering.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditService auditService;
    private final MongoTemplate mongoTemplate;

    public CategoryService(CategoryRepository categoryRepository, 
                            AuditService auditService,
                            MongoTemplate mongoTemplate) {
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderByShowOrderAsc();
    }

    @Transactional
    public Category createCategory(Category category) {
        // If showOrder is specified, shift existing categories up
        if (category.getShowOrder() > 0) {
            shiftCategoryOrdersUp(category.getShowOrder());
        } else {
            // Auto-assign next available order (append to end)
            category.setShowOrder(getNextAvailableOrder());
        }
        Category saved = categoryRepository.save(category);
        log.info("New category created: {} at order {}", saved.getName(), saved.getShowOrder());
        auditService.logAction("CATEGORY_CREATED", 
            "Created category: " + saved.getName() + " at order " + saved.getShowOrder());
        return saved;
    }


    @Transactional
    public Category updateCategory(String id, Category category) {
        return categoryRepository.findById(id)
                .map(existing -> {
                    String oldName = existing.getName();
                    int oldOrder = existing.getShowOrder();
                    int newOrder = category.getShowOrder();
                    
                    // Update name if provided
                    if (category.getName() != null) {
                        existing.setName(category.getName());
                    }
                    
                    // Handle order change
                    if (newOrder != 0 && newOrder != oldOrder) {
                        renumberCategoryOrders(oldOrder, newOrder, id);
                        existing.setShowOrder(newOrder);
                    }
                    
                    Category updated = categoryRepository.save(existing);
                    log.info("Category updated: {} order {} -> {}", 
                        updated.getName(), oldOrder, newOrder);
                    auditService.logAction("CATEGORY_UPDATED", 
                        "Updated category: " + oldName + " order " + oldOrder + " -> " + newOrder);
                    return updated;
                })
                .orElse(null);
    }

    @Transactional
    public void deleteCategory(String id) {
        categoryRepository.findById(id).ifPresent(category -> {
            int deletedOrder = category.getShowOrder();
            categoryRepository.delete(category);
            // Shift down categories after the deleted one to fill the gap
            shiftCategoryOrdersDown(deletedOrder);
            log.info("Category {} at order {} deleted, subsequent categories renumbered", 
                category.getName(), deletedOrder);
            auditService.logAction("CATEGORY_DELETED", 
                "Deleted category: " + category.getName() + " at order " + deletedOrder);
        });
    }

    // Shift categories up (increment showOrder) - used when inserting at a position
    private void shiftCategoryOrdersUp(int fromOrder) {
        Query query = new Query(Criteria.where("showOrder").gte(fromOrder));
        Update update = new Update().inc("showOrder", 1);
        mongoTemplate.updateMulti(query, update, Category.class);
    }

    // Shift categories down (decrement showOrder) - used when deleting to fill gap
    private void shiftCategoryOrdersDown(int fromOrder) {
        Query query = new Query(Criteria.where("showOrder").gt(fromOrder));
        Update update = new Update().inc("showOrder", -1);
        mongoTemplate.updateMulti(query, update, Category.class);
    }

    // Renumber categories when moving from oldOrder to newOrder
    private void renumberCategoryOrders(int oldOrder, int newOrder, String excludeId) {
        if (oldOrder < newOrder) {
            // Moving down: shift categories between old+1 and new down by 1
            Query query = new Query(Criteria.where("showOrder").gt(oldOrder).lte(newOrder)
                    .and("id").ne(excludeId));
            Update update = new Update().inc("showOrder", -1);
            mongoTemplate.updateMulti(query, update, Category.class);
        } else {
            // Moving up: shift categories between new and old-1 up by 1
            Query query = new Query(Criteria.where("showOrder").gte(newOrder).lt(oldOrder)
                    .and("id").ne(excludeId));
            Update update = new Update().inc("showOrder", 1);
            mongoTemplate.updateMulti(query, update, Category.class);
        }
    }

    // Get next available order (max + 1)
    private int getNextAvailableOrder() {
        List<Category> categories = categoryRepository.findAllByOrderByShowOrderDesc();
        if (categories.isEmpty()) return 1;
        return categories.get(0).getShowOrder() + 1;
    }

}

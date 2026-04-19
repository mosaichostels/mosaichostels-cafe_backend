package com.hostel.ordering.service;

import com.hostel.ordering.model.Category;
import com.hostel.ordering.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CategoryService {
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

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

    public List<Category> getMenuCategories() {
        List<Category> all = categoryRepository.findAllByOrderByShowOrderAsc();
        return all.stream()
                .filter(c -> Category.TYPE_MENU.equals(c.getType()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Category> getEssentialCategories() {
        List<Category> all = categoryRepository.findAllByOrderByShowOrderAsc();
        return all.stream()
                .filter(c -> Category.TYPE_ESSENTIAL.equals(c.getType()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public Category createCategory(Category category) {
        String type = category.getType() != null ? category.getType() : Category.TYPE_MENU;
        category.setType(type);
        
        if (category.getShowOrder() > 0) {
            shiftCategoryOrdersUp(category.getShowOrder(), type);
        } else {
            category.setShowOrder(getNextAvailableOrder(type));
        }
        Category saved = categoryRepository.save(category);
        log.info("New category created: {} at order {} type {}", saved.getName(), saved.getShowOrder(), saved.getType());
        auditService.logAction("CATEGORY_CREATED",
            "Created category: " + saved.getName() + " at order " + saved.getShowOrder() + " (" + saved.getType() + ")");
        return saved;
    }

    @Transactional
    public Category updateCategory(String id, Category category) {
        return categoryRepository.findById(id)
                .map(existing -> {
                    String oldName = existing.getName();
                    int oldOrder = existing.getShowOrder();
                    int newOrder = category.getShowOrder();

                    if (category.getName() != null) {
                        existing.setName(category.getName());
                    }

                    if (newOrder > 0 && newOrder != oldOrder) {
                        String type = category.getType() != null ? category.getType() : existing.getType();
                        log.info("Triggering reorder for type {}: {} -> {}", type, oldOrder, newOrder);
                        renumberCategoryOrders(oldOrder, newOrder, id, type);
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
            String type = category.getType();
            categoryRepository.delete(category);
            shiftCategoryOrdersDown(deletedOrder, type);
            log.info("Category {} at order {} deleted, subsequent categories renumbered",
                category.getName(), deletedOrder);
            auditService.logAction("CATEGORY_DELETED",
                "Deleted category: " + category.getName() + " at order " + deletedOrder);
        });
    }

    private void shiftCategoryOrdersUp(int fromOrder, String type) {
        Query query = new Query(Criteria.where("showOrder").gte(fromOrder)
                .and("type").is(type));
        Update update = new Update().inc("showOrder", 1);
        mongoTemplate.updateMulti(query, update, Category.class);
    }

    private void shiftCategoryOrdersDown(int fromOrder, String type) {
        Query query = new Query(Criteria.where("showOrder").gt(fromOrder)
                .and("type").is(type));
        Update update = new Update().inc("showOrder", -1);
        mongoTemplate.updateMulti(query, update, Category.class);
    }

    private void renumberCategoryOrders(int oldOrder, int newOrder, String excludeId, String type) {
        if (oldOrder < newOrder) {
            Query query = new Query(Criteria.where("showOrder").gt(oldOrder).lte(newOrder)
                    .and("id").ne(excludeId).and("type").is(type));
            Update update = new Update().inc("showOrder", -1);
            mongoTemplate.updateMulti(query, update, Category.class);
        } else {
            Query query = new Query(Criteria.where("showOrder").gte(newOrder).lt(oldOrder)
                    .and("id").ne(excludeId).and("type").is(type));
            Update update = new Update().inc("showOrder", 1);
            mongoTemplate.updateMulti(query, update, Category.class);
        }
    }

    private int getNextAvailableOrder(String type) {
        String finalType = type != null ? type : Category.TYPE_MENU;
        List<Category> categories = categoryRepository.findAllByOrderByShowOrderAsc().stream()
                .filter(c -> finalType.equals(c.getType()))
                .collect(java.util.stream.Collectors.toList());
        if (categories.isEmpty()) return 1;
        return categories.stream()
                .mapToInt(c -> c.getShowOrder())
                .max()
                .orElse(0) + 1;
    }

}

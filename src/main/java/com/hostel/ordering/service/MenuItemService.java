package com.hostel.ordering.service;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.repository.MenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;

@Service
public class MenuItemService {
    private static final Logger log = LoggerFactory.getLogger(MenuItemService.class);

    private final MenuItemRepository menuItemRepository;
    private final AuditService auditService;

    public MenuItemService(MenuItemRepository menuItemRepository, AuditService auditService) {
        this.menuItemRepository = menuItemRepository;
        this.auditService = auditService;
    }

    public MenuItem createMenuItem(MenuItem menuItem) {
        menuItem.setCreatedAt(System.currentTimeMillis());
        MenuItem saved = menuItemRepository.save(menuItem);
        log.info("New menu item created: {}", saved.getName());
        auditService.logAction("MENU_ITEM_CREATED", "Created menu item: " + saved.getName() + " with price " + saved.getPrice());
        return saved;
    }

    public MenuItem getMenuItem(String id) {
        return menuItemRepository.findById(id).orElse(null);
    }

    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAll();
    }

    public List<MenuItem> getAvailableMenuItems(String category, String sort) {
        List<MenuItem> items = (category != null && !category.isBlank())
                ? menuItemRepository.findByAvailableTrueAndCategoryOrderByNameAsc(category)
                : menuItemRepository.findByAvailableTrueOrderByNameAsc();

        String finalSort = (sort == null || sort.isBlank()) ? "price_asc" : sort;

        Comparator<MenuItem> comparator = switch (finalSort) {
            case "price_asc" -> Comparator.comparingDouble(MenuItem::getPrice);
            case "price_desc" -> Comparator.comparingDouble(MenuItem::getPrice).reversed();
            case "newest" -> Comparator.comparingLong(MenuItem::getCreatedAt).reversed();
            case "name_asc" -> Comparator.comparing(MenuItem::getName);
            default -> null;
        };

        if (comparator != null) {
            return items.stream().sorted(comparator).toList();
        }

        return items;
    }

    public List<MenuItem> searchMenuItems(String query, boolean availableOnly) {
        // The repository interpolates this straight into a $regex, and the search endpoint is
        // unauthenticated, so an unescaped query is a regex anyone on the internet can run -
        // including a catastrophically backtracking one. Pattern.quote makes it a literal,
        // matching what OrderRepositoryImpl already does for the order search.
        String literal = java.util.regex.Pattern.quote(query == null ? "" : query);
        if (availableOnly) {
            return menuItemRepository.findByNameContainingIgnoreCaseAndAvailableTrueOrderByNameAsc(literal);
        }
        return menuItemRepository.findByNameContainingIgnoreCaseOrderByNameAsc(literal);
    }

    public MenuItem updateMenuItem(String id, MenuItem menuItem) {
        return menuItemRepository.findById(id)
                .map(existing -> {
                    String oldName = existing.getName();
                    if (menuItem.getName() != null) existing.setName(menuItem.getName());
                    if (menuItem.getDescription() != null) existing.setDescription(menuItem.getDescription());
                    if (menuItem.getPrice() != null) existing.setPrice(menuItem.getPrice());
                    if (menuItem.getCategory() != null) existing.setCategory(menuItem.getCategory());
                    if (menuItem.getAvailable() != null) existing.setAvailable(menuItem.getAvailable());
                    MenuItem updated = menuItemRepository.save(existing);
                    log.info("Menu item updated: {} -> {}", oldName, updated.getName());
                    auditService.logAction("MENU_ITEM_UPDATED", "Updated menu item: " + oldName + " -> " + updated.getName());
                    return updated;
                })
                .orElse(null);
    }

    public void deleteMenuItem(String id) {
        menuItemRepository.findById(id).ifPresent(item -> {
            item.setDeleted(true);
            menuItemRepository.save(item);
            log.info("Menu item {} marked as deleted", item.getName());
            auditService.logAction("MENU_ITEM_DELETED", "Deleted menu item: " + item.getName());
        });
    }
}

package com.hostel.ordering.service;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.repository.MenuItemRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;

    public MenuItemService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    public MenuItem createMenuItem(MenuItem menuItem) {
        menuItem.setCreatedAt(System.currentTimeMillis());
        menuItem.setUpdatedAt(System.currentTimeMillis());
        return menuItemRepository.save(menuItem);
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

        if (sort != null) {
            Comparator<MenuItem> comparator = switch (sort) {
                case "price_asc" -> Comparator.comparingDouble(MenuItem::getPrice);
                case "price_desc" -> Comparator.comparingDouble(MenuItem::getPrice).reversed();
                case "newest" -> Comparator.comparingLong(MenuItem::getCreatedAt).reversed();
                default -> null;
            };
            if (comparator != null) {
                return items.stream().sorted(comparator).toList();
            }
        }

        return items;
    }

    public List<MenuItem> searchMenuItems(String query, boolean availableOnly) {
        if (availableOnly) {
            return menuItemRepository.findByNameContainingIgnoreCaseAndAvailableTrueOrderByNameAsc(query);
        }
        return menuItemRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
    }

    public MenuItem updateMenuItem(String id, MenuItem menuItem) {
        return menuItemRepository.findById(id)
                .map(existing -> {
                    if (menuItem.getName() != null) existing.setName(menuItem.getName());
                    if (menuItem.getDescription() != null) existing.setDescription(menuItem.getDescription());
                    if (menuItem.getPrice() != null) existing.setPrice(menuItem.getPrice());
                    if (menuItem.getCategory() != null) existing.setCategory(menuItem.getCategory());
                    if (menuItem.getAvailable() != null) existing.setAvailable(menuItem.getAvailable());
                    existing.setUpdatedAt(System.currentTimeMillis());
                    return menuItemRepository.save(existing);
                })
                .orElse(null);
    }

    public void deleteMenuItem(String id) {
        menuItemRepository.deleteById(id);
    }
}

package com.hostel.ordering.service;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;

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
        List<MenuItem> items;

        if (category != null && !category.isEmpty()) {
            items = menuItemRepository.findByAvailableTrueAndCategoryOrderByNameAsc(category);
        } else {
            items = menuItemRepository.findByAvailableTrueOrderByNameAsc();
        }

        if (sort != null) {
            items = switch (sort) {
                case "price_asc" -> items.stream().sorted(Comparator.comparing(MenuItem::getPrice))
                        .collect(Collectors.toList());
                case "price_desc" -> items.stream().sorted(Comparator.comparing(MenuItem::getPrice).reversed())
                        .collect(Collectors.toList());
                case "newest" -> items.stream().sorted(Comparator.comparing(MenuItem::getCreatedAt).reversed())
                        .collect(Collectors.toList());
                default -> items;
            };
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
        return menuItemRepository.findById(id).map(existingMenuItem -> {
            if (menuItem.getName() != null)
                existingMenuItem.setName(menuItem.getName());
            if (menuItem.getDescription() != null)
                existingMenuItem.setDescription(menuItem.getDescription());
            if (menuItem.getPrice() != null)
                existingMenuItem.setPrice(menuItem.getPrice());
            if (menuItem.getCategory() != null)
                existingMenuItem.setCategory(menuItem.getCategory());
            if (menuItem.getAvailable() != null)
                existingMenuItem.setAvailable(menuItem.getAvailable());

            existingMenuItem.setUpdatedAt(System.currentTimeMillis());

            return menuItemRepository.save(existingMenuItem);
        }).orElse(null);
    }

    public void deleteMenuItem(String id) {
        menuItemRepository.deleteById(id);
    }
}

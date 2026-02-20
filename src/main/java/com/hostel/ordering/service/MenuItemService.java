package com.hostel.ordering.service;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.repository.MenuItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MenuItemService {

    @Autowired
    private MenuItemRepository menuItemRepository;

    public MenuItem createMenuItem(MenuItem menuItem) {
        menuItem.setCreatedAt(System.currentTimeMillis());
        menuItem.setUpdatedAt(System.currentTimeMillis());
        return menuItemRepository.save(menuItem);
    }

    public MenuItem getMenuItem(String id) {
        Optional<MenuItem> menuItem = menuItemRepository.findById(id);
        return menuItem.orElse(null);
    }

    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAll();
    }

    public List<MenuItem> getAvailableMenuItems(String category, String subCategory, String sort) {
        List<MenuItem> items;

        if (category != null && !category.isEmpty() && subCategory != null && !subCategory.isEmpty()) {
            items = menuItemRepository.findByAvailableTrueAndCategoryAndSubCategoryOrderByNameAsc(category, subCategory);
        } else if (category != null && !category.isEmpty()) {
            items = menuItemRepository.findByAvailableTrueAndCategoryOrderByNameAsc(category);
        } else if (subCategory != null && !subCategory.isEmpty()) {
            items = menuItemRepository.findByAvailableTrueAndSubCategoryOrderByNameAsc(subCategory);
        } else {
            items = menuItemRepository.findByAvailableTrueOrderByNameAsc();
        }

        // Apply sort
        if (sort != null) {
            switch (sort) {
                case "price_asc":
                    items = items.stream().sorted((a, b) -> Double.compare(a.getPrice(), b.getPrice())).collect(Collectors.toList());
                    break;
                case "price_desc":
                    items = items.stream().sorted((a, b) -> Double.compare(b.getPrice(), a.getPrice())).collect(Collectors.toList());
                    break;
                case "newest":
                    items = items.stream().sorted((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt())).collect(Collectors.toList());
                    break;
                default: // name_asc - already sorted
                    break;
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

    public List<String> getSubCategories(String category) {
        List<MenuItem> items;
        if (category != null && !category.isEmpty()) {
            items = menuItemRepository.findByCategoryOrderByNameAsc(category);
        } else {
            items = menuItemRepository.findAll();
        }
        return items.stream()
                .map(MenuItem::getSubCategory)
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public MenuItem updateMenuItem(String id, MenuItem menuItem) {
        Optional<MenuItem> optionalMenuItem = menuItemRepository.findById(id);

        if (optionalMenuItem.isPresent()) {
            MenuItem existingMenuItem = optionalMenuItem.get();

            if (menuItem.getName() != null) existingMenuItem.setName(menuItem.getName());
            if (menuItem.getDescription() != null) existingMenuItem.setDescription(menuItem.getDescription());
            if (menuItem.getPrice() != null) existingMenuItem.setPrice(menuItem.getPrice());
            if (menuItem.getCategory() != null) existingMenuItem.setCategory(menuItem.getCategory());
            if (menuItem.getSubCategory() != null) existingMenuItem.setSubCategory(menuItem.getSubCategory());
            if (menuItem.getAvailable() != null) existingMenuItem.setAvailable(menuItem.getAvailable());

            existingMenuItem.setUpdatedAt(System.currentTimeMillis());

            return menuItemRepository.save(existingMenuItem);
        }

        return null;
    }

    public void deleteMenuItem(String id) {
        menuItemRepository.deleteById(id);
    }
}

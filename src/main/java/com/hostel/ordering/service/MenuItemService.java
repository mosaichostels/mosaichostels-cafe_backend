package com.hostel.ordering.service;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.repository.MenuItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

    public List<MenuItem> getAvailableMenuItems() {
        return menuItemRepository.findByAvailableTrueOrderByNameAsc();
    }

    public List<MenuItem> searchMenuItemsByName(String name) {
        return menuItemRepository.findByNameContainingIgnoreCaseOrderByNameAsc(name);
    }

    public MenuItem updateMenuItem(String id, MenuItem menuItem) {
        Optional<MenuItem> optionalMenuItem = menuItemRepository.findById(id);
        
        if (optionalMenuItem.isPresent()) {
            MenuItem existingMenuItem = optionalMenuItem.get();
            
            if (menuItem.getName() != null) existingMenuItem.setName(menuItem.getName());
            if (menuItem.getDescription() != null) existingMenuItem.setDescription(menuItem.getDescription());
            if (menuItem.getPrice() != null) existingMenuItem.setPrice(menuItem.getPrice());
            if (menuItem.getCategory() != null) existingMenuItem.setCategory(menuItem.getCategory());
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

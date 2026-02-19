package com.hostel.ordering.controller;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.service.MenuItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu-items")
public class MenuItemController {

    @Autowired
    private MenuItemService menuItemService;

    @PostMapping
    public ResponseEntity<?> createMenuItem(@RequestBody MenuItem menuItem) {
        try {
            MenuItem created = menuItemService.createMenuItem(menuItem);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating menu item: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMenuItem(@PathVariable String id) {
        try {
            MenuItem menuItem = menuItemService.getMenuItem(id);
            if (menuItem != null) {
                return ResponseEntity.ok(menuItem);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Menu item not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching menu item: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllMenuItems() {
        try {
            List<MenuItem> menuItems = menuItemService.getAllMenuItems();
            return ResponseEntity.ok(menuItems);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching menu items: " + e.getMessage());
        }
    }

    @GetMapping("/available")
    public ResponseEntity<?> getAvailableMenuItems() {
        try {
            List<MenuItem> menuItems = menuItemService.getAvailableMenuItems();
            return ResponseEntity.ok(menuItems);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching available menu items: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchMenuItems(@RequestParam String name) {
        try {
            List<MenuItem> menuItems = menuItemService.searchMenuItemsByName(name);
            return ResponseEntity.ok(menuItems);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error searching menu items: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMenuItem(@PathVariable String id, @RequestBody MenuItem menuItem) {
        try {
            MenuItem updated = menuItemService.updateMenuItem(id, menuItem);
            if (updated != null) {
                return ResponseEntity.ok(updated);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Menu item not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating menu item: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMenuItem(@PathVariable String id) {
        try {
            menuItemService.deleteMenuItem(id);
            return ResponseEntity.ok("Menu item deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting menu item: " + e.getMessage());
        }
    }
}

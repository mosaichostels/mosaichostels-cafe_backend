package com.hostel.ordering.controller;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.service.MenuItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/menu-items")
public class MenuItemController {

    private final MenuItemService menuItemService;

    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MenuItem> createMenuItem(@RequestBody MenuItem menuItem) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemService.createMenuItem(menuItem));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItem> getMenuItem(@PathVariable String id) {
        MenuItem menuItem = menuItemService.getMenuItem(id);
        return menuItem != null ? ResponseEntity.ok(menuItem) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<MenuItem>> getAllMenuItems() {
        return ResponseEntity.ok(menuItemService.getAllMenuItems());
    }

    @GetMapping("/available")
    public ResponseEntity<List<MenuItem>> getAvailableMenuItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "name_asc") String sort) {
        return ResponseEntity.ok(menuItemService.getAvailableMenuItems(category, sort));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MenuItem>> searchMenuItems(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "false") boolean availableOnly) {
        return ResponseEntity.ok(menuItemService.searchMenuItems(q, availableOnly));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MenuItem> updateMenuItem(@PathVariable String id, @RequestBody MenuItem menuItem) {
        MenuItem updated = menuItemService.updateMenuItem(id, menuItem);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteMenuItem(@PathVariable String id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.ok("Menu item deleted successfully");
    }
}

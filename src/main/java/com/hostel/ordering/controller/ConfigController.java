package com.hostel.ordering.controller;

import com.hostel.ordering.model.Category;
import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.service.CategoryService;
import com.hostel.ordering.service.DormitoryService;
import com.hostel.ordering.service.OrderStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/config")
public class ConfigController {

    private final CategoryService categoryService;
    private final DormitoryService dormitoryService;
    private final OrderStatusService orderStatusService;

    public ConfigController(CategoryService categoryService,
                            DormitoryService dormitoryService,
                            OrderStatusService orderStatusService) {
        this.categoryService = categoryService;
        this.dormitoryService = dormitoryService;
        this.orderStatusService = orderStatusService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // Backend-driven Dormitories
        List<String> dormNames = dormitoryService.getAllDormitories().stream()
                .map(Dormitory::getName)
                .collect(Collectors.toList());
        config.put("dormitories", dormNames);
        
        // Backend-driven Order Statuses
        config.put("orderStatuses", orderStatusService.getAllStatuses());
        
        // Backend-driven Categories sorted by showOrder
        List<Category> categories = categoryService.getAllCategories();
        config.put("categories", categories);
        
        return ResponseEntity.ok(config);
    }

    // Category Management
    @PostMapping("/categories")
    public Category addCategory(@RequestBody Category category) {
        return categoryService.createCategory(category);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
    }

    // Dormitory Management
    @PostMapping("/dormitories")
    public Dormitory addDormitory(@RequestBody Dormitory dormitory) {
        return dormitoryService.addDormitory(dormitory);
    }

    @DeleteMapping("/dormitories/{id}")
    public void deleteDormitory(@PathVariable String id) {
        dormitoryService.deleteDormitory(id);
    }

    @GetMapping("/dormitories")
    public List<Dormitory> getFullDormitories() {
        return dormitoryService.getAllDormitories();
    }

    // Status Management
    @PostMapping("/statuses")
    public OrderStatusConfig addStatus(@RequestBody OrderStatusConfig status) {
        return orderStatusService.addStatus(status);
    }

    @DeleteMapping("/statuses/{id}")
    public void deleteStatus(@PathVariable String id) {
        orderStatusService.deleteStatus(id);
    }
}

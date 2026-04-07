package com.hostel.ordering.controller;

import com.hostel.ordering.model.Category;
import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.service.CategoryService;
import com.hostel.ordering.service.DormitoryService;
import com.hostel.ordering.service.OrderStatusService;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;
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

        List<String> dormNames = dormitoryService.getAllDormitories().stream()
                .map(Dormitory::getName)
                .collect(Collectors.toList());
        config.put("dormitories", dormNames);

        config.put("orderStatuses", orderStatusService.getAllStatuses());

        List<Category> categories = categoryService.getAllCategories();
        config.put("categories", categories);

        return ResponseEntity.ok(config);
    }

    @PostMapping("/categories")
    public Category addCategory(@RequestBody Category category) {
        return categoryService.createCategory(category);
    }

    @PutMapping("/categories/{id}")
    public Category updateCategory(@PathVariable String id, @RequestBody Category category) {
        return categoryService.updateCategory(id, category);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
    }

    @PostMapping("/dormitories")
    public Dormitory addDormitory(@RequestBody Dormitory dormitory) {
        return dormitoryService.addDormitory(dormitory);
    }

    @PutMapping("/dormitories/{id}")
    public Dormitory updateDormitory(@PathVariable String id, @RequestBody Dormitory dormitory) {
        return dormitoryService.updateDormitory(id, dormitory);
    }

    @DeleteMapping("/dormitories/{id}")
    public void deleteDormitory(@PathVariable String id) {
        dormitoryService.deleteDormitory(id);
    }

    @GetMapping("/dormitories")
    public List<Dormitory> getFullDormitories() {
        return dormitoryService.getAllDormitories();
    }

    @PostMapping("/statuses")
    public OrderStatusConfig addStatus(@RequestBody OrderStatusConfig status) {
        return orderStatusService.addStatus(status);
    }

    @DeleteMapping("/statuses/{id}")
    public void deleteStatus(@PathVariable String id) {
        orderStatusService.deleteStatus(id);
    }
}

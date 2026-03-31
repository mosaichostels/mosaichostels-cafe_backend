package com.hostel.ordering.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.ordering.model.Category;
import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.model.OrderStatusConfig;
import com.hostel.ordering.service.CategoryService;
import com.hostel.ordering.service.DormitoryService;
import com.hostel.ordering.service.OrderStatusService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Configuration
public class SeedingConfig {

    @Bean
    public CommandLineRunner initDatabase(
            CategoryService categoryService,
            DormitoryService dormitoryService,
            OrderStatusService orderStatusService,
            ResourceLoader resourceLoader) {
        return args -> {
            ObjectMapper mapper = new ObjectMapper();
            Resource resource = resourceLoader.getResource("classpath:initial-data.json");

            if (!resource.exists()) {
                System.out.println("No initial-data.json found. Skipping seeding.");
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                Map<String, List<Object>> data = mapper.readValue(is, new TypeReference<Map<String, List<Object>>>() {});

                // Seed Categories
                if (categoryService.count() == 0 && data.containsKey("categories")) {
                    List<Category> categories = mapper.convertValue(
                            data.get("categories"), new TypeReference<List<Category>>() {});
                    categories.forEach(categoryService::addCategory);
                    System.out.println("Seeded " + categories.size() + " categories from JSON.");
                }

                // Seed Dormitories
                if (dormitoryService.count() == 0 && data.containsKey("dormitories")) {
                    List<Dormitory> dorms = mapper.convertValue(
                            data.get("dormitories"), new TypeReference<List<Dormitory>>() {});
                    dorms.forEach(dormitoryService::addDormitory);
                    System.out.println("Seeded " + dorms.size() + " dormitories from JSON.");
                }

                // Seed Order Statuses
                if (orderStatusService.count() == 0 && data.containsKey("orderStatuses")) {
                    List<OrderStatusConfig> statuses = mapper.convertValue(
                            data.get("orderStatuses"), new TypeReference<List<OrderStatusConfig>>() {});
                    statuses.forEach(orderStatusService::addStatus);
                    System.out.println("Seeded " + statuses.size() + " order statuses from JSON.");
                }

            } catch (Exception e) {
                System.err.println("Error seeding database from JSON: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}

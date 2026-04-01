package com.hostel.ordering.service;

import com.hostel.ordering.model.Category;
import com.hostel.ordering.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderByShowOrderAsc();
    }

    public Category createCategory(Category category) {
        return categoryRepository.save(category);
    }

    public Category createIfNotExists(Category category) {
        Optional<Category> existing = categoryRepository.findByName(category.getName());
        if (existing.isEmpty()) {
            return categoryRepository.save(category);
        }
        return existing.get();
    }

    public void deleteCategory(String id) {
        categoryRepository.deleteById(id);
    }

    public long count() {
        return categoryRepository.count();
    }
}

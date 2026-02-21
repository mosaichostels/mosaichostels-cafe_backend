package com.hostel.ordering.repository;

import com.hostel.ordering.model.MenuItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends MongoRepository<MenuItem, String> {
    List<MenuItem> findByCategoryOrderByNameAsc(String category);

    List<MenuItem> findByCategoryAndSubCategoryOrderByNameAsc(String category, String subCategory);

    List<MenuItem> findByAvailableTrueOrderByNameAsc();

    List<MenuItem> findByAvailableTrueOrderByCreatedAtDesc();

    List<MenuItem> findByAvailableTrueOrderByPriceAsc();

    List<MenuItem> findByAvailableTrueOrderByPriceDesc();

    List<MenuItem> findByAvailableTrueAndCategoryOrderByNameAsc(String category);

    List<MenuItem> findByAvailableTrueAndSubCategoryOrderByNameAsc(String subCategory);

    List<MenuItem> findByAvailableTrueAndCategoryAndSubCategoryOrderByNameAsc(String category, String subCategory);

    List<MenuItem> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<MenuItem> findByNameContainingIgnoreCaseAndAvailableTrueOrderByNameAsc(String name);

    List<MenuItem> findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByNameAsc(String desc, String name);
}

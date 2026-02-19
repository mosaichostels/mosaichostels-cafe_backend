package com.hostel.ordering.repository;

import com.hostel.ordering.model.MenuItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends MongoRepository<MenuItem, String> {
    List<MenuItem> findByCategoryOrderByNameAsc(String category);
    List<MenuItem> findByAvailableTrueOrderByNameAsc();
    List<MenuItem> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}

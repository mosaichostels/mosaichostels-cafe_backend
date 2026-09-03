package com.hostel.ordering.repository;

import com.hostel.ordering.model.MenuItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface MenuItemRepository extends MongoRepository<MenuItem, String> {

    @Query("{ 'available': true, 'deleted': { $ne: true } }")
    List<MenuItem> findByAvailableTrueOrderByNameAsc();

    @Query("{ 'available': true, 'category': ?0, 'deleted': { $ne: true } }")
    List<MenuItem> findByAvailableTrueAndCategoryOrderByNameAsc(String category);

    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'deleted': { $ne: true } }")
    List<MenuItem> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'available': true, 'deleted': { $ne: true } }")
    List<MenuItem> findByNameContainingIgnoreCaseAndAvailableTrueOrderByNameAsc(String name);
}

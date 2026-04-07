package com.hostel.ordering.repository;

import com.hostel.ordering.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {
    List<Category> findAllByOrderByShowOrderAsc();
    List<Category> findAllByOrderByShowOrderDesc();
    Optional<Category> findByName(String name);
}

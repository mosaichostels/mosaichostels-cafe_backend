package com.hostel.ordering.repository;

import com.hostel.ordering.model.OtherEssential;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OtherEssentialRepository extends MongoRepository<OtherEssential, String> {
    List<OtherEssential> findByAvailableTrueOrderByNameAsc();

    List<OtherEssential> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<OtherEssential> findByNameContainingIgnoreCaseAndAvailableTrueOrderByNameAsc(String name);

    List<OtherEssential> findByDescriptionContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByNameAsc(String desc, String name);

    List<OtherEssential> findByCategoryOrderByNameAsc(String category);

    List<OtherEssential> findByAvailableTrueAndCategoryOrderByNameAsc(String category);
}

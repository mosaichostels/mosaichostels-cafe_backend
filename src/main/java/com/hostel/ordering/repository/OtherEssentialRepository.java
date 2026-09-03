package com.hostel.ordering.repository;

import com.hostel.ordering.model.OtherEssential;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface OtherEssentialRepository extends MongoRepository<OtherEssential, String> {

    @Query("{ 'available': true, 'deleted': { $ne: true } }")
    List<OtherEssential> findByAvailableTrueOrderByNameAsc();
    
    @Query("{ 'available': true, 'category': ?0, 'deleted': { $ne: true } }")
    List<OtherEssential> findByAvailableTrueAndCategoryOrderByNameAsc(String category);

    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'deleted': { $ne: true } }")
    List<OtherEssential> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'available': true, 'deleted': { $ne: true } }")
    List<OtherEssential> findByNameContainingIgnoreCaseAndAvailableTrueOrderByNameAsc(String name);
}

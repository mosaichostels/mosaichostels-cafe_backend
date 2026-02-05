package com.hostel.ordering.repository;

import com.hostel.ordering.model.OtherEssential;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OtherEssentialRepository extends MongoRepository<OtherEssential, String> {
    List<OtherEssential> findByAvailableTrueOrderByNameAsc();
}

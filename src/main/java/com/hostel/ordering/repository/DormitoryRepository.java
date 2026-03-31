package com.hostel.ordering.repository;

import com.hostel.ordering.model.Dormitory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DormitoryRepository extends MongoRepository<Dormitory, String> {
    Optional<Dormitory> findByName(String name);
}

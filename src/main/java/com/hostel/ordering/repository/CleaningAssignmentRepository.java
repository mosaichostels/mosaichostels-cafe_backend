package com.hostel.ordering.repository;

import com.hostel.ordering.model.CleaningAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CleaningAssignmentRepository extends MongoRepository<CleaningAssignment, String> {
    Optional<CleaningAssignment> findByTaskKey(String taskKey);

    List<CleaningAssignment> findByDepartureDate(String departureDate);
}

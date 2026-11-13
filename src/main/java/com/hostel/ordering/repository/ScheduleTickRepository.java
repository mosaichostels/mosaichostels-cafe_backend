package com.hostel.ordering.repository;

import com.hostel.ordering.model.ScheduleTick;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ScheduleTickRepository extends MongoRepository<ScheduleTick, String> {

    Optional<ScheduleTick> findByDateAndTaskKeyAndSlot(String date, String taskKey, String slot);

    /** Both ends INCLUSIVE (a derived findByDateBetween is exclusive on Spring Data MongoDB). */
    @Query("{ 'date': { $gte: ?0, $lte: ?1 } }")
    List<ScheduleTick> ticksInRange(String from, String to);
}

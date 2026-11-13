package com.hostel.ordering.repository;

import com.hostel.ordering.model.ScheduleTick;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScheduleTickRepository extends MongoRepository<ScheduleTick, String> {

    Optional<ScheduleTick> findByDateAndTaskKeyAndSlot(String date, String taskKey, String slot);

    List<ScheduleTick> findByDateBetween(String from, String to);
}

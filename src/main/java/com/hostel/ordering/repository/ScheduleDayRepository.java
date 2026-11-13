package com.hostel.ordering.repository;

import com.hostel.ordering.model.ScheduleDay;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScheduleDayRepository extends MongoRepository<ScheduleDay, String> {

    boolean existsByDate(String date);

    /** yyyy-MM-dd strings sort like dates, so a string range is a date range. */
    List<ScheduleDay> findByDateBetweenOrderByDate(String from, String to);
}

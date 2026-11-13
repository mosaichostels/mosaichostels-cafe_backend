package com.hostel.ordering.repository;

import com.hostel.ordering.model.ScheduleDay;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ScheduleDayRepository extends MongoRepository<ScheduleDay, String> {

    boolean existsByDate(String date);

    /**
     * Both ends INCLUSIVE: a derived findByDateBetween is exclusive on Spring Data MongoDB ($gt/$lt), which left
     * today out of a today-to-today read. yyyy-MM-dd strings sort like dates, so a string range is a date range.
     */
    @Query(value = "{ 'date': { $gte: ?0, $lte: ?1 } }", sort = "{ 'date': 1 }")
    List<ScheduleDay> daysInRange(String from, String to);
}

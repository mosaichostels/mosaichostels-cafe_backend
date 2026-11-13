package com.hostel.ordering.repository;

import com.hostel.ordering.model.ScheduleReminder;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScheduleReminderRepository extends MongoRepository<ScheduleReminder, String> {
}

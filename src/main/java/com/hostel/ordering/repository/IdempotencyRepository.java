package com.hostel.ordering.repository;

import com.hostel.ordering.model.IdempotencyRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdempotencyRepository extends MongoRepository<IdempotencyRecord, String> {
}

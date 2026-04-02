package com.hostel.ordering.repository;

import com.hostel.ordering.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditRepository extends MongoRepository<AuditLog, String> {
    void deleteByTimestampLessThan(long timestamp);
}

package com.hostel.ordering.repository;

import com.hostel.ordering.model.OrderStatusConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderStatusRepository extends MongoRepository<OrderStatusConfig, String> {
    Optional<OrderStatusConfig> findByValue(String value);
}

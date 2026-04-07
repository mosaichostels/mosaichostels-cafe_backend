package com.hostel.ordering.repository;

import com.hostel.ordering.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface OrderRepository extends MongoRepository<Order, String>, OrderRepositoryCustom {
}

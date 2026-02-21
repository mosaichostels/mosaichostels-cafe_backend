package com.hostel.ordering.repository;

import com.hostel.ordering.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByStatusOrderByCreatedAtDesc(String status);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByStatusAndDormitoryOrderByCreatedAtDesc(String status, String dormitory);

    List<Order> findByDormitoryOrderByCreatedAtDesc(String dormitory);

    List<Order> findByBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(String bookingName);

    List<Order> findByStatusAndBookingNameContainingIgnoreCaseOrderByCreatedAtDesc(String status, String bookingName);

    List<Order> findByPhoneNumberContainingOrderByCreatedAtDesc(String phoneNumber);

    List<Order> findByStatusAndPhoneNumberContainingOrderByCreatedAtDesc(String status, String phoneNumber);

    List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(Long from, Long to);

    List<Order> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(String status, Long from, Long to);
}

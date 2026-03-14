package com.hostel.ordering.repository;

import com.hostel.ordering.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    // ── Paginated queries (used by web admin panel) ───────────────────────────
    Page<Order> findByStatus(String status, Pageable pageable);
    Page<Order> findAll(Pageable pageable);
    Page<Order> findByStatusAndDormitory(String status, String dormitory, Pageable pageable);
    Page<Order> findByDormitory(String dormitory, Pageable pageable);
    Page<Order> findByBookingNameContainingIgnoreCase(String bookingName, Pageable pageable);
    Page<Order> findByStatusAndBookingNameContainingIgnoreCase(String status, String bookingName, Pageable pageable);
    Page<Order> findByPhoneNumberContaining(String phoneNumber, Pageable pageable);
    Page<Order> findByStatusAndPhoneNumberContaining(String status, String phoneNumber, Pageable pageable);
    Page<Order> findByCreatedAtBetween(Long from, Long to, Pageable pageable);
    Page<Order> findByStatusAndCreatedAtBetween(String status, Long from, Long to, Pageable pageable);

    // ── Unpaginated queries (used by Android app and delete operations) ───────
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

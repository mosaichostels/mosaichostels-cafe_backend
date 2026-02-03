package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String id;
    private String bookingName;
    private String dormitory;
    private String phoneNumber;
    private List<OrderItem> items;
    private Double totalAmount;
    private String status;
    private Long createdAt;
    private Long updatedAt;
}

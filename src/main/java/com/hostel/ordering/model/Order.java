package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {
    @Id
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

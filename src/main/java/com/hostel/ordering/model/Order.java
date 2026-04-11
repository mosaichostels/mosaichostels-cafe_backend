package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
@Document(collection = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    private String id;

    @NotBlank(message = "Booking name is required")
    private String bookingName;

    @NotBlank(message = "Dormitory is required")
    private String dormitory;

    @NotEmpty(message = "Items list cannot be empty")
    private List<OrderItem> items;

    private Double totalAmount;
    private String status;
    private String createdBy;
    private String updatedBy;
    private Long createdAt;
    private Long updatedAt;
}

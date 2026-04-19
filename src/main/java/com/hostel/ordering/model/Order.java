package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Document(collection = "orders")
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

    public Order() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBookingName() { return bookingName; }
    public void setBookingName(String bookingName) { this.bookingName = bookingName; }

    public String getDormitory() { return dormitory; }
    public void setDormitory(String dormitory) { this.dormitory = dormitory; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
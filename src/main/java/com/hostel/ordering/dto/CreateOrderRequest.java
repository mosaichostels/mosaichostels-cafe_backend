package com.hostel.ordering.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hostel.ordering.model.OrderItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateOrderRequest {
    @NotBlank(message = "Booking name is required")
    private String bookingName;

    @NotBlank(message = "Dormitory is required")
    private String dormitory;

    @NotEmpty(message = "Items list cannot be empty")
    private List<OrderItem> items;

    private Double totalAmount;

    public CreateOrderRequest() {}

    public String getBookingName() {
        return bookingName;
    }

    public void setBookingName(String bookingName) {
        this.bookingName = bookingName;
    }

    public String getDormitory() {
        return dormitory;
    }

    public void setDormitory(String dormitory) {
        this.dormitory = dormitory;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }
}

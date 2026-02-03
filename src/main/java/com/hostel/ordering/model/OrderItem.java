package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private String menuItemId;
    private String menuItemName;
    private Integer quantity;
    private Double price;
    private Double subtotal;
}

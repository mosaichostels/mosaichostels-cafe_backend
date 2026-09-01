package com.hostel.ordering.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OrderItem {
    @NotBlank(message = "Menu item ID cannot be blank")
    private String menuItemId;

    @NotBlank(message = "Menu item name cannot be blank")
    private String menuItemName;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 50, message = "Quantity must not exceed 50")
    private Integer quantity;

    @NotNull(message = "Price cannot be null")
    @Min(value = 0, message = "Price cannot be negative")
    private Double price;

    private Double subtotal;

    @NotBlank(message = "Type cannot be blank")
    private String type;

    public OrderItem() {}

    public String getMenuItemId() { return menuItemId; }
    public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }

    public String getMenuItemName() { return menuItemName; }
    public void setMenuItemName(String menuItemName) { this.menuItemName = menuItemName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
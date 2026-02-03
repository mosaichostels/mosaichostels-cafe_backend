package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuItem {
    private String id;
    private String name;
    private String description;
    private Double price;
    private String category;
    private Boolean available;
    private Long createdAt;
    private Long updatedAt;
}

package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "other_essentials")
public class OtherEssential {

    @Id
    private String id;
    private String name;
    private String description;
    private Double price;
    private String category;
    private Boolean available;
    private Long createdAt;
    private Long updatedAt;
}

package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    private String username;

    private String action; // e.g., "LOGIN_SUCCESS", "ORDER_STATUS_UPDATED"

    private String details; // e.g., "Order ID: 123 -> DELIVERED"

    private long timestamp;
}

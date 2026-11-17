package com.hostel.ordering.dto;

public record StatusUpdateRequest(String status, Long expectedVersion) {
}

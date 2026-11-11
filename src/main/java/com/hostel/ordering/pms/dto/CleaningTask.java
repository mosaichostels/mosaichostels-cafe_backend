package com.hostel.ordering.pms.dto;

/**
 * One room's cleaning to-do for a departure day. {@code turnovers} is how many departures leave that room that
 * day. No guest or booking fields by design. {@code assignmentId}/{@code version} are null until assigned.
 */
public record CleaningTask(String taskKey, String roomId, String unitId, String roomName, String roomType,
        String departureDate, int turnovers, String assigneeUsername, String status, String notes,
        String assignmentId, Long version) {}

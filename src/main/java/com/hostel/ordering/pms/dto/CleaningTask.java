package com.hostel.ordering.pms.dto;

/**
 * One room's cleaning to-do for a departure day. {@code turnovers} is how many departures leave that room that
 * day. {@code hkStatus} is the room's current eZee housekeeping label (e.g. Clean, Dirty), "" when unknown. No guest or booking fields by design.
 */
public record CleaningTask(String taskKey, String roomId, String unitId, String roomName, String roomType,
        String departureDate, int turnovers, String hkStatus) {}

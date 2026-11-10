package com.hostel.ordering.pms.dto;

/** One room on the housekeeping board. hkStatus is eZee's raw string; guestName is the only personal field. */
public record RoomStatus(String roomId, String unitId, String roomTypeId, String name, String roomType,
        String hkStatus, boolean blocked, boolean occupied, String guestName) {}

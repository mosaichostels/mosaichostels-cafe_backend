package com.hostel.ordering.pms.dto;

/** One row of an arrivals, departures or in-house list. No phone, email or address by design. */
public record BookingSummary(String bookingId, String guestName, String room, String roomType, String arrival,
        String departure, int nights, String status, String source) {}

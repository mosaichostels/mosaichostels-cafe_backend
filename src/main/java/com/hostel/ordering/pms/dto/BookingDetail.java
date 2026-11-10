package com.hostel.ordering.pms.dto;

import java.util.List;

/** Booking detail for the staff hub. The only DTO that carries phone and email (call/WhatsApp buttons). */
public record BookingDetail(BookingSummary summary, List<FolioSummary> folios, String phone, String email) {}

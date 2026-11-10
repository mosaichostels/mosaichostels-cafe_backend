package com.hostel.ordering.pms.dto;

/** One guest-search hit. Name only: no phone, email or address by design. */
public record GuestHit(String id, String name, boolean vip, int stays) {}

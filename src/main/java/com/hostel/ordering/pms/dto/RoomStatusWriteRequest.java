package com.hostel.ordering.pms.dto;

/** status is CLEAN or DIRTY (checked by the service, so an unknown value is a 400); remarks is optional. */
public record RoomStatusWriteRequest(String unitId, String status, String remarks) {}

package com.hostel.ordering.pms.dto;

/** Ids and statuses only: never a guest or remark text. hkStatus is what eZee reported after the write (blank if unread). */
public record RoomStatusWriteResult(String roomId, String unitId, String status, String hkStatus, boolean confirmed) {}

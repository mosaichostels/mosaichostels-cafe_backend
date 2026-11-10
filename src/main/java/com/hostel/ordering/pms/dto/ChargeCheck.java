package com.hostel.ordering.pms.dto;

import java.math.BigDecimal;

/**
 * One café order on the reconciliation screen. eZeeStatus: FOUND, MISSING, UNKNOWN or N/A (not posted).
 * FOUND means at least one charge line of the order was found in eZee, not that every line was.
 */
public record ChargeCheck(String orderId, String bookingName, String room, BigDecimal amount, String ourStatus,
        String eZeeStatus) {}

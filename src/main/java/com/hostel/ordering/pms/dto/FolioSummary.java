package com.hostel.ordering.pms.dto;

import java.math.BigDecimal;

/** One folio of a booking with its totals; money stays BigDecimal. */
public record FolioSummary(String folioNo, String currency, BigDecimal total, BigDecimal paid, BigDecimal due) {}

package com.hostel.ordering.pms;

public class PmsIdempotencyMismatchException extends PmsException {

    public PmsIdempotencyMismatchException(String message) {
        super("IDEMPOTENCY_MISMATCH", message);
    }
}

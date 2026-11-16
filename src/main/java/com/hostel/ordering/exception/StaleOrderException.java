package com.hostel.ordering.exception;

public class StaleOrderException extends RuntimeException {
    public StaleOrderException() {
        super("This order changed elsewhere. Refresh and try again.");
    }
}

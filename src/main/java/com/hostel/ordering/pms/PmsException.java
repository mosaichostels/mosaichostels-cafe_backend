package com.hostel.ordering.pms;

/** Base of every PMS gateway failure. The message is for logs only; clients get a fixed text per code. */
public abstract class PmsException extends RuntimeException {

    private final String code;

    protected PmsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

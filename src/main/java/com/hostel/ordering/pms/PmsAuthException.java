package com.hostel.ordering.pms;

public class PmsAuthException extends PmsException {

    public PmsAuthException(String message) {
        super("PMS_AUTH", message);
    }
}

package com.hostel.ordering.pms;

public class PmsNotEnabledException extends PmsException {

    public PmsNotEnabledException(String message) {
        super("PMS_NOT_ENABLED", message);
    }
}

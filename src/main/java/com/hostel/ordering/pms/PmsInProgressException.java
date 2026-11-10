package com.hostel.ordering.pms;

public class PmsInProgressException extends PmsException {

    public PmsInProgressException(String message) {
        super("PMS_IN_PROGRESS", message);
    }
}

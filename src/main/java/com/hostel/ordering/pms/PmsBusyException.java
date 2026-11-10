package com.hostel.ordering.pms;

public class PmsBusyException extends PmsException {

    public PmsBusyException(String message) {
        super("PMS_BUSY", message);
    }
}

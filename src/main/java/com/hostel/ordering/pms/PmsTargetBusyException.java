package com.hostel.ordering.pms;

public class PmsTargetBusyException extends PmsException {

    public PmsTargetBusyException(String message) {
        super("PMS_TARGET_BUSY", message);
    }
}

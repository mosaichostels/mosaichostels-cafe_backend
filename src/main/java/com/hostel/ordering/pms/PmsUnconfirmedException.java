package com.hostel.ordering.pms;

public class PmsUnconfirmedException extends PmsException {

    public PmsUnconfirmedException(String message) {
        super("PMS_UNCONFIRMED", message);
    }
}

package com.hostel.ordering.pms;

public class PmsFeatureDisabledException extends PmsException {

    public PmsFeatureDisabledException(String message) {
        super("PMS_FEATURE_DISABLED", message);
    }
}

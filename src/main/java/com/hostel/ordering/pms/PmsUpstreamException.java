package com.hostel.ordering.pms;

public class PmsUpstreamException extends PmsException {

    public PmsUpstreamException(String message) {
        super("PMS_UPSTREAM", message);
    }
}

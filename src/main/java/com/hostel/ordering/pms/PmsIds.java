package com.hostel.ordering.pms;

import java.util.regex.Pattern;

/** Validation for ids that go into an eZee request: never forward a raw client string. */
public final class PmsIds {

    private static final Pattern BOOKING_ID = Pattern.compile("[0-9]{1,20}(-[0-9]{1,3})?");

    private PmsIds() {}

    public static String bookingId(String id) {
        if (id == null || !BOOKING_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid booking id");
        }
        return id;
    }
}

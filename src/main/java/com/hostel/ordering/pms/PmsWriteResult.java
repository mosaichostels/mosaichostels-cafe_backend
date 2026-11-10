package com.hostel.ordering.pms;

/** A guarded write's reply; {@code confirmed=false} means eZee answered but the read-back did not match. */
public record PmsWriteResult<T>(T value, boolean confirmed) {
}

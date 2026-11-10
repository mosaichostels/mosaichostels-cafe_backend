package com.hostel.ordering.pms;

/**
 * A guarded write's reply; {@code confirmed=false} means eZee answered but the outcome is not proven (the
 * read-back did not match, or the call outlived the stuck bound) and the claim is parked for an admin.
 * {@code value} may be null with {@code confirmed=true}: a replay of a claim an admin resolved APPLIED, or one
 * whose result could not be serialised, has no stored value. Callers must null-check it.
 */
public record PmsWriteResult<T>(T value, boolean confirmed) {
}

package com.hostel.ordering.exception;

/**
 * An earlier charge attempt got no reply from eZee, so the item may already be on the guest's
 * folio. The admin must check the folio and explicitly acknowledge before another attempt.
 */
public class ChargeUnconfirmedException extends RuntimeException {
    public ChargeUnconfirmedException(String message) {
        super(message);
    }
}

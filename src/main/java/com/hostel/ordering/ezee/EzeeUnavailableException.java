package com.hostel.ordering.ezee;

/** eZee could not be reached or answered unusably; distinct from "no guest found". */
public class EzeeUnavailableException extends RuntimeException {
    public EzeeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

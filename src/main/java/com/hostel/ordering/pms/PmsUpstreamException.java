package com.hostel.ordering.pms;

/**
 * eZee failed or answered with an error. {@link #isSent()} is true (the default) when the request may have
 * reached eZee, so a write's outcome is unknown; false only when the failure happened before any I/O.
 */
public class PmsUpstreamException extends PmsException {

    private final boolean sent;

    public PmsUpstreamException(String message) {
        this(message, true);
    }

    private PmsUpstreamException(String message, boolean sent) {
        super("PMS_UPSTREAM", message);
        this.sent = sent;
    }

    /** A failure raised before any request left this process: eZee cannot have applied anything. */
    public static PmsUpstreamException notSent(String message) {
        return new PmsUpstreamException(message, false);
    }

    public boolean isSent() {
        return sent;
    }
}

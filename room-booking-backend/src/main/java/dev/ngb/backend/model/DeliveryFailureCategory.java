package dev.ngb.backend.model;

/**
 * Why an attempt failed, which decides whether retrying can help.
 */
public enum DeliveryFailureCategory {
    /** The provider refused the message itself. */
    PROVIDER_REJECTED,
    /** The address or number is not usable. */
    INVALID_DESTINATION,
    /** The destination is suppressed and must not be tried. */
    SUPPRESSED,
    /** The provider throttled the account. */
    RATE_LIMITED,
    /** The provider credentials were refused. */
    AUTHENTICATION,
    /** No answer within the deadline. */
    TIMEOUT,
    /** A platform-side fault before submission. */
    INTERNAL
}

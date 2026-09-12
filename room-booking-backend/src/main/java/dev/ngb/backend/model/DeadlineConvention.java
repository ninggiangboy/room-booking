package dev.ngb.backend.model;

/**
 * How a review submission deadline is resolved to an instant.
 */
public enum DeadlineConvention {
    /** End of the civil day in the listing time zone. */
    LISTING_LOCAL_MIDNIGHT,
    /** End of the civil day in the guest time zone. */
    GUEST_LOCAL_MIDNIGHT,
    /** A fixed duration after completion, with no civil rounding. */
    EXACT_INSTANT
}

package dev.ngb.backend.model;

/**
 * Whether a claim is still consuming inventory.
 *
 * <p>Only {@link #ACTIVE} claims fall inside the exclusion constraint's predicate, which is what lets
 * a cancelled stay free its nights the moment it is released rather than waiting for a cleanup job.
 * The other states are retained so the calendar's history stays explainable.</p>
 */
public enum ClaimStatus {
    /** Currently consuming the nights. */
    ACTIVE,
    /** Deliberately given up. */
    RELEASED,
    /** Lapsed without being consumed. */
    EXPIRED,
    /** Replaced by another claim, such as a hold becoming a booking. */
    SUPERSEDED
}

package dev.ngb.backend.stay.internal.model.stay;

/**
 * What booking did with an outcome proposal.
 */
public enum StayOutcomeResult {
    /** Not yet answered. */
    PENDING,
    /** Booking made the transition and named it. */
    ACCEPTED_BY_BOOKING,
    /** Booking refused it, with a reason. */
    REJECTED_BY_BOOKING,
    /** Operations withdrew the proposal. */
    WITHDRAWN
}

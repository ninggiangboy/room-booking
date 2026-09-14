package dev.ngb.backend.support.internal.model.policy;

import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;

/**
 * The occurrence time basis of {@code support_policy_versions}.
 */
public enum OccurrenceTimeBasis {

    /** Booking accepted at. */
    BOOKING_ACCEPTED_AT,

    /** Occurrence at. */
    OCCURRENCE_AT,

    /** Case opened at. */
    CASE_OPENED_AT,

    /** Stay check in at. */
    STAY_CHECK_IN_AT
}

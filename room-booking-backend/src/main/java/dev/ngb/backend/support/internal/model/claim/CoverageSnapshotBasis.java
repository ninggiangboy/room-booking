package dev.ngb.backend.support.internal.model.claim;

import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;

/**
 * The snapshot basis of {@code coverage_snapshots}.
 */
public enum CoverageSnapshotBasis {

    /** Booking accepted. */
    BOOKING_ACCEPTED,

    /** Stay start. */
    STAY_START,

    /** Claim occurrence. */
    CLAIM_OCCURRENCE,

    /** Qualifying event. */
    QUALIFYING_EVENT
}

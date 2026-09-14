package dev.ngb.backend.stay.internal.model.stay;

import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;

/**
 * What evidence says about the guest being there.
 *
 * <p>Evidence, not a booking decision: booking owns check-in and completion.</p>
 */
public enum StayPresenceState {
    /** Nothing has been observed. */
    NONE,
    /** Somebody reported an arrival. */
    ARRIVAL_REPORTED,
    /** Booking recorded a check-in from this evidence. */
    CHECKED_IN_RECORDED,
    /** Somebody reported a departure. */
    DEPARTURE_REPORTED
}

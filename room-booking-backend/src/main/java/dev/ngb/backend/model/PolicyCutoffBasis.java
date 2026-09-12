package dev.ngb.backend.model;

/**
 * Which clock a cancellation cutoff is measured against.
 *
 * <p>"Forty-eight hours before check-in" is meaningless without saying whose forty-eight hours.
 * Settling in the wrong zone moves real money, so the basis is stored with the terms rather than
 * assumed by the evaluator.</p>
 */
public enum PolicyCutoffBasis {
    /** The property's own wall clock, which is what a guest arriving expects. */
    PROPERTY_LOCAL,
    /** The guest's wall clock at booking time. */
    GUEST_LOCAL,
    /** Absolute time, with no civil calendar involved. */
    UTC,
    /** The zone snapshotted on the booking, which survives a property moving zones. */
    BOOKING_TIME_ZONE
}

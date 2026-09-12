package dev.ngb.backend.model;

/**
 * Whether a replacement of this booking is currently in flight.
 *
 * <p>A modification is a proposal, not an in-place edit: the original booking stays valid and
 * enforceable until the replacement commits. This dimension exists so that a booking can say a
 * change is pending without pretending the change has already happened.</p>
 */
public enum BookingChangeState {
    /** No modification is in flight. */
    NONE,
    /** A proposal exists and the original still stands. */
    PENDING,
    /** The proposal committed and this booking reflects it. */
    APPLIED,
    /** The proposal was declined; the original is unchanged. */
    REJECTED,
    /** The proposal lapsed before anyone acted on it. */
    EXPIRED
}

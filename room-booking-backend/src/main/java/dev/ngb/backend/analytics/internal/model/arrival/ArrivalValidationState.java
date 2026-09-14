package dev.ngb.backend.analytics.internal.model.arrival;

/**
 * What the collector decided about an arrival.
 *
 * <p>Rejected and quarantined arrivals are stored, not discarded. An unregistered producer or an
 * implausible client clock is evidence, and losing it would hide the problem.</p>
 */
public enum ArrivalValidationState {

    /** Passed validation and is eligible for the analytical layer. */
    ACCEPTED,

    /** An envelope identity already accepted; kept as evidence that a producer re-sent. */
    DUPLICATE,

    /** Failed a validation rule; stored exactly as it arrived, skewed clock and all. */
    REJECTED,

    /** Held for debugging under a shorter retention, not published to anything. */
    QUARANTINED
}

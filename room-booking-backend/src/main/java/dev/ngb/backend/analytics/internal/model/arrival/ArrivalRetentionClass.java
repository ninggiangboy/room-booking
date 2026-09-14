package dev.ngb.backend.analytics.internal.model.arrival;

/**
 * How long an arrival is kept and under what rule.
 *
 * <p>Quarantine is deliberately its own class with a shorter horizon, so that debugging evidence
 * cannot accumulate into a second, ungoverned copy of the stream.</p>
 */
public enum ArrivalRetentionClass {

    /** The retention the contract declares. */
    STANDARD,

    /** Kept for less than the contract default, for high-volume or low-value streams. */
    SHORT,

    /** Debugging horizon, deliberately shorter than the accepted stream. */
    QUARANTINE,

    /** Retained beyond its normal horizon because a legal hold applies. */
    LEGAL_HOLD
}

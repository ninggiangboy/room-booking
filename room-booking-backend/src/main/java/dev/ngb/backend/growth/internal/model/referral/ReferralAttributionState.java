package dev.ngb.backend.growth.internal.model.referral;

/**
 * Where a referral stands between being recorded and being paid or undone.
 */
public enum ReferralAttributionState {

    /** Recorded, waiting for the qualifying event. */
    PENDING,

    /** The qualifying booking happened. */
    QUALIFIED,

    /** Refused, for a recorded reason. */
    REJECTED,

    /** Qualified and then undone, its rewards reversed first. */
    REVERSED
}

package dev.ngb.backend.trust.internal.model.intervention;

/**
 * How an appeal was answered.
 *
 * <p>A grant restores eligible capability prospectively and triggers the domain commands that follow;
 * it does not erase audit history and does not by itself create a monetary entitlement. Any remedy
 * is a referral to support, which owns that decision.</p>
 */
public enum RiskAppealOutcome {
    /** The original decision stands. */
    ORIGINAL_CONFIRMED,
    /** The original decision is replaced. */
    GRANTED,
    /** Part of the original decision is replaced. */
    PARTIALLY_GRANTED,
    /** Not a matter this appeal path decides. */
    OUT_OF_SCOPE;
}

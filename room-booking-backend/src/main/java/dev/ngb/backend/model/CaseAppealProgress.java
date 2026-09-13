package dev.ngb.backend.model;

/**
 * The appeal state of {@code damage_claims}.
 */
public enum CaseAppealProgress {

    /** None. */
    NONE,

    /** Eligible. */
    ELIGIBLE,

    /** Submitted. */
    SUBMITTED,

    /** Reviewing. */
    REVIEWING,

    /** Decided. */
    DECIDED,

    /** Exhausted. */
    EXHAUSTED
}

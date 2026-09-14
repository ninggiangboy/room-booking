package dev.ngb.backend.support.internal.model.claim;

/**
 * The submission outcome of {@code payment_dispute_strategies}.
 */
public enum DisputeSubmissionOutcome {

    /** Not submitted. */
    NOT_SUBMITTED,

    /** Pending. */
    PENDING,

    /** Accepted. */
    ACCEPTED,

    /** Rejected. */
    REJECTED,

    /** Unknown. */
    UNKNOWN,

    /** Failed. */
    FAILED
}

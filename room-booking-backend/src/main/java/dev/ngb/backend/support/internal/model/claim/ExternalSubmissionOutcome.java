package dev.ngb.backend.support.internal.model.claim;

/**
 * The outcome of {@code external_claim_submissions}.
 */
public enum ExternalSubmissionOutcome {

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

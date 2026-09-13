package dev.ngb.backend.model;

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

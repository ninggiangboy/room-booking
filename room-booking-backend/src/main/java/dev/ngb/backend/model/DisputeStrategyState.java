package dev.ngb.backend.model;

/**
 * The state of {@code payment_dispute_strategies}.
 */
public enum DisputeStrategyState {

    /** Pending strategy. */
    PENDING_STRATEGY,

    /** Evidence ready. */
    EVIDENCE_READY,

    /** Submitting. */
    SUBMITTING,

    /** Submitted. */
    SUBMITTED,

    /** Accepted. */
    ACCEPTED,

    /** Unknown. */
    UNKNOWN,

    /** Closed. */
    CLOSED
}

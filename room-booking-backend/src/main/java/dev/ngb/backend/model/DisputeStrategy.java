package dev.ngb.backend.model;

/**
 * The strategy of {@code payment_dispute_strategies}.
 */
public enum DisputeStrategy {

    /** Undecided. */
    UNDECIDED,

    /** Accept. */
    ACCEPT,

    /** Represent. */
    REPRESENT,

    /** Partial represent. */
    PARTIAL_REPRESENT,

    /** Refer to legal. */
    REFER_TO_LEGAL
}

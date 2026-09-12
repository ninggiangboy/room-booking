package dev.ngb.backend.model;

/**
 * How far one call to the payout provider has got.
 *
 * <p>The submission fence applies here exactly as it does to payments: only an operation that never
 * left can be abandoned outright, and a timeout resolves to {@link #UNKNOWN} rather than to a
 * failure the platform cannot prove.</p>
 */
public enum PayoutOperationState {
    /** Written down and not yet sent. */
    PREPARED,
    /** Crossing to the provider now. */
    SUBMITTING,
    /** The provider has it; no outcome observed yet. */
    SUBMITTED,
    /** A verified outcome says it worked. */
    SUCCEEDED,
    /** A verified outcome says it did not, with a category. */
    FAILED,
    /** Submitted with no proven outcome; query, never resend. */
    UNKNOWN,
    /** Abandoned while it was still purely local. */
    CANCELLED_BEFORE_SUBMISSION
}

package dev.ngb.backend.model;

/**
 * The financial execution state of {@code support_cases}.
 */
public enum FinancialExecutionState {

    /** None. */
    NONE,

    /** Pending. */
    PENDING,

    /** Executing. */
    EXECUTING,

    /** Partial. */
    PARTIAL,

    /** Unknown. */
    UNKNOWN,

    /** Complete. */
    COMPLETE,

    /** Reversed. */
    REVERSED
}

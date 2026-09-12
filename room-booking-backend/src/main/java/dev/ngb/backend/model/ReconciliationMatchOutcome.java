package dev.ngb.backend.model;

/**
 * The verdict on one comparison.
 *
 * <p>A difference is a first-class fact with a name, not an absence of a match. Only
 * {@link #MATCHED_EXACT} and {@link #MATCHED_AGGREGATE} close a comparison, and only from the
 * deterministic tiers of the matching ladder.</p>
 */
public enum ReconciliationMatchOutcome {
    /** One internal fact and one external row agree on a deterministic key. */
    MATCHED_EXACT,
    /** A set on one side sums exactly to the other, under an approved rule. */
    MATCHED_AGGREGATE,
    /** They disagree only because of a known settlement cutoff or delay. */
    EXPECTED_TIMING_DIFFERENCE,
    /** Same fact, different amount. */
    AMOUNT_MISMATCH,
    /** Same fact, different currency. */
    CURRENCY_MISMATCH,
    /** Same fact, contradictory states. */
    STATUS_MISMATCH,
    /** The external source reported the same movement twice. */
    DUPLICATE_EXTERNAL,
    /** The platform recorded the same movement twice. */
    DUPLICATE_INTERNAL,
    /** The external side shows a movement the platform has no record of. */
    MISSING_INTERNAL,
    /** The platform expected a movement the external side does not show. */
    MISSING_EXTERNAL,
    /** A reference on one side names nothing on the other. */
    UNKNOWN_REFERENCE,
    /** Fell outside the run's coverage interval. */
    OUTSIDE_COVERAGE,
    /** The row could not be read well enough to compare. */
    PARSER_OR_SCHEMA_ERROR
}

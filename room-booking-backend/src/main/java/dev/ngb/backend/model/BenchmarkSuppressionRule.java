package dev.ngb.backend.model;

/**
 * What a cohort does when it falls below its own privacy floor.
 */
public enum BenchmarkSuppressionRule {

    /** Publish the row without numbers, so the host can tell suppression from a failure. */
    SUPPRESS_VALUE,

    /** Publish nothing at all for the period. */
    SUPPRESS_ROW,

    /** Fall back to a broader peer set that does clear the floor. */
    WIDEN_COHORT
}

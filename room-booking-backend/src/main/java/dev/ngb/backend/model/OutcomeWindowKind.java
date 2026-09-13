package dev.ngb.backend.model;

/**
 * The window an outcome aggregate covers.
 */
public enum OutcomeWindowKind {

    /** Rolling 7d. */
    ROLLING_7D,

    /** Rolling 30d. */
    ROLLING_30D,

    /** Rolling 90d. */
    ROLLING_90D,

    /** Rolling 365d. */
    ROLLING_365D,

    /** All time. */
    ALL_TIME
}

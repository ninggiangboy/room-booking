package dev.ngb.backend.model;

/**
 * Whether the access was permitted.
 */
public enum RiskAccessOutcome {
    /** Allowed in full. */
    PERMITTED,
    /** Refused. */
    DENIED,
    /** Allowed in part. */
    PARTIAL,
    /** Could not be completed. */
    ERRORED;
}

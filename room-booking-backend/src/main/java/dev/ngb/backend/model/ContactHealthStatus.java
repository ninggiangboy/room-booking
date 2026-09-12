package dev.ngb.backend.model;

/**
 * Whether a destination is still worth sending to.
 */
public enum ContactHealthStatus {
    /** Delivering normally. */
    HEALTHY,
    /** Soft failures are accumulating. */
    DEGRADED,
    /** Sending is stopped; the reason and source are recorded. */
    SUPPRESSED,
    /** The destination is permanently unusable. */
    UNROUTABLE
}

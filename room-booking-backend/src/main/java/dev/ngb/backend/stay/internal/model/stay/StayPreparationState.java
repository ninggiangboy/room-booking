package dev.ngb.backend.stay.internal.model.stay;

/**
 * How ready the property is for this stay.
 *
 * <p>Independent of access, presence and outcome: a property can be ready while its lock is not.</p>
 */
public enum StayPreparationState {
    /** No preparation work has been planned yet. */
    NOT_SCHEDULED,
    /** Tasks exist with service windows. */
    SCHEDULED,
    /** At least one task has started. */
    IN_PROGRESS,
    /** Every critical task completed with the evidence it required. */
    READY,
    /** A collision or an incomplete critical task stands in the way. */
    BLOCKED,
    /** Reporting is missing or stale; readiness must not be assumed. */
    UNKNOWN
}

package dev.ngb.backend.stay.internal.model.task;

/**
 * Where a maintenance record stands.
 */
public enum MaintenanceState {
    /** Raised, not yet assessed. */
    REPORTED,
    /** Assessed and categorized. */
    TRIAGED,
    /** Work is planned for a window. */
    SCHEDULED,
    /** Work has started. */
    IN_PROGRESS,
    /** Fixed, with a time recorded. */
    RESOLVED,
    /** Postponed, with a reason. */
    DEFERRED,
    /** No longer a defect. */
    CANCELLED
}

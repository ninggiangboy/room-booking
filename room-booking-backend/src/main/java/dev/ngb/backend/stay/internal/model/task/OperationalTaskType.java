package dev.ngb.backend.stay.internal.model.task;

/**
 * The kind of preparation work a task represents.
 */
public enum OperationalTaskType {
    /** Cleaning between stays. */
    TURNOVER_CLEANING,
    /** General condition inspection. */
    INSPECTION,
    /** Linen change or delivery. */
    LINEN,
    /** Restocking supplies. */
    CONSUMABLES,
    /** Confirming a repair actually holds. */
    MAINTENANCE_VERIFICATION,
    /** Meeting the guest with a key. */
    KEY_HANDOFF,
    /** Preparing guest registration paperwork. */
    REGISTRATION_PREPARATION,
    /** Inspecting the property after departure. */
    CHECKOUT_INSPECTION
}

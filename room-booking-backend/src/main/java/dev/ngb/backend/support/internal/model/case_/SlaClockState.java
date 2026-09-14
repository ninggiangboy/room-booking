package dev.ngb.backend.support.internal.model.case_;

/**
 * The state of {@code case_sla_clocks}.
 */
public enum SlaClockState {

    /** Running. */
    RUNNING,

    /** Paused. */
    PAUSED,

    /** Completed. */
    COMPLETED,

    /** Breached. */
    BREACHED,

    /** Cancelled. */
    CANCELLED
}

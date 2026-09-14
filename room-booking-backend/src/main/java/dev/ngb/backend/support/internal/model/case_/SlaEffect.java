package dev.ngb.backend.support.internal.model.case_;

/**
 * The sla effect of {@code case_transitions}.
 */
public enum SlaEffect {

    /** None. */
    NONE,

    /** Start. */
    START,

    /** Pause. */
    PAUSE,

    /** Resume. */
    RESUME,

    /** Complete. */
    COMPLETE,

    /** Reset. */
    RESET
}

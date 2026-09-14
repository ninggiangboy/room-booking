package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * Where an experiment stands across all of its epochs.
 */
public enum ExperimentStatus {

    /** Being designed. */
    DRAFT,

    /** Design accepted. */
    REVIEWED,

    /** Waiting to start. */
    SCHEDULED,

    /** Assigning and exposing units. */
    RUNNING,

    /** Delivery suspended, history kept. */
    PAUSED,

    /** Ended early. */
    STOPPED,

    /** Ran to its planned end. */
    COMPLETED,

    /** Analysed and concluded. */
    ANALYZED,

    /** Closed out. */
    ARCHIVED,

    /** Its behaviour was reverted. */
    ROLLED_BACK
}

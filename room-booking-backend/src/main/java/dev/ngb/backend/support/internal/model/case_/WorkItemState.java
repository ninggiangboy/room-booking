package dev.ngb.backend.support.internal.model.case_;

/**
 * The state of {@code case_work_items}.
 */
public enum WorkItemState {

    /** Ready. */
    READY,

    /** Claimed. */
    CLAIMED,

    /** In progress. */
    IN_PROGRESS,

    /** Blocked. */
    BLOCKED,

    /** Completed. */
    COMPLETED,

    /** Cancelled. */
    CANCELLED,

    /** Dead letter. */
    DEAD_LETTER
}

package dev.ngb.backend.support.internal.model.case_;

/**
 * The priority basis of {@code case_work_items}.
 */
public enum WorkPriorityBasis {

    /** Deterministic severity. */
    DETERMINISTIC_SEVERITY,

    /** Deadline. */
    DEADLINE,

    /** Monetary exposure. */
    MONETARY_EXPOSURE,

    /** Model ordering. */
    MODEL_ORDERING,

    /** Manual. */
    MANUAL
}

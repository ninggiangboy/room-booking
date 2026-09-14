package dev.ngb.backend.trust.internal.model.review;

/**
 * What decides where a task sits in the queue.
 *
 * <p>An urgent safety task may be ordered only by severity or deadline. A model may order peers; it
 * may not demote a tier-four task, and the database refuses to record one that says it did.</p>
 */
public enum ReviewPriorityBasis {
    /** A severity floor set by policy. */
    DETERMINISTIC_SEVERITY,
    /** A statutory or provider deadline. */
    DEADLINE,
    /** Money or inventory at stake. */
    EXPOSURE,
    /** A model score ordering comparable tasks. */
    MODEL_ASSISTED;
}

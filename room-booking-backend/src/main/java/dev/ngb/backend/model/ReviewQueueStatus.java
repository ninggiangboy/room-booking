package dev.ngb.backend.model;

/**
 * Whether a queue accepts work.
 */
public enum ReviewQueueStatus {
    /** Accepting and dispatching work. */
    ACTIVE,
    /** Holding work without dispatching it. */
    PAUSED,
    /** Closed; existing work was moved elsewhere. */
    RETIRED;
}

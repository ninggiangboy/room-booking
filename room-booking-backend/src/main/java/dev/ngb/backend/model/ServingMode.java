package dev.ngb.backend.model;

/**
 * How a prediction was requested.
 */
public enum ServingMode {

    /** Requested inside a user-facing request, under a deadline. */
    ONLINE,

    /** Scored ahead of time by a scheduled job. */
    BATCH,

    /** Produced for comparison, with nothing acting on it. */
    SHADOW
}

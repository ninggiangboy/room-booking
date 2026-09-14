package dev.ngb.backend.admin.internal.model.configuration;

/**
 * Where a rollout stage stands.
 */
public enum RolloutState {

    /** In progress. */
    RUNNING,

    /** Finished and left in place. */
    COMPLETED,

    /** Stopped before anything became effective. */
    ABORTED,

    /** Reversed by a later stage. */
    ROLLED_BACK
}

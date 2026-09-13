package dev.ngb.backend.model;

/**
 * Where one run stands.
 */
public enum PipelineRunState {

    /** Claimed but not started. */
    PENDING,

    /** In progress under a lease. */
    RUNNING,

    /** Finished and produced its output. */
    SUCCEEDED,

    /** Finished without a usable output; the failure class says why. */
    FAILED,

    /** Stopped deliberately. */
    CANCELLED
}

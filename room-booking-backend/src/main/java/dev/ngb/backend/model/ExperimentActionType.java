package dev.ngb.backend.model;

/**
 * The command being issued against an experiment.
 */
public enum ExperimentActionType {

    /** Set a start. */
    SCHEDULE,

    /** Begin assigning. */
    START,

    /** Suspend delivery. */
    PAUSE,

    /** Resume delivery. */
    RESUME,

    /** End early. */
    STOP,

    /** Make the treatment the standing behaviour. */
    ROLLOUT,

    /** Return to the approved previous behaviour. */
    ROLLBACK,

    /** Close the experiment record. */
    ARCHIVE
}

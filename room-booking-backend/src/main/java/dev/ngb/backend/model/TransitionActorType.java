package dev.ngb.backend.model;

/**
 * The actor type of {@code case_transitions}.
 */
public enum TransitionActorType {

    /** Agent. */
    AGENT,

    /** Supervisor. */
    SUPERVISOR,

    /** System. */
    SYSTEM,

    /** Participant. */
    PARTICIPANT,

    /** Scheduler. */
    SCHEDULER
}

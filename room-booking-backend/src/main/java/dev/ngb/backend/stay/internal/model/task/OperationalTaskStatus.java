package dev.ngb.backend.stay.internal.model.task;

/**
 * Where a preparation task stands.
 *
 * <p>Completion is an attestation plus the evidence the task asked for; a trigger refuses it
 * otherwise, and refuses it while a dependency is still open.</p>
 */
public enum OperationalTaskStatus {
    /** Created, not yet scheduled. */
    PLANNED,
    /** Given a service window. */
    SCHEDULED,
    /** Given an owner. */
    ASSIGNED,
    /** Started. */
    IN_PROGRESS,
    /** Cannot proceed, with a recorded reason. */
    BLOCKED,
    /** Attested complete with required evidence. */
    COMPLETED,
    /** No longer needed. */
    CANCELLED,
    /** Reopened after completion, with a reason. */
    REOPENED
}

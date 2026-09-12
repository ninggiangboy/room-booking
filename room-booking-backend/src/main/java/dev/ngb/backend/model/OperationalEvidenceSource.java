package dev.ngb.backend.model;

/**
 * Where a piece of operational evidence came from.
 */
public enum OperationalEvidenceSource {
    /** The person the task was assigned to. */
    ASSIGNEE,
    /** The host. */
    HOST,
    /** The guest. */
    GUEST,
    /** A listing operator. */
    OPERATOR,
    /** A support agent. */
    SUPPORT,
    /** An external service. */
    PROVIDER,
    /** A device reading with no person attached. */
    DEVICE,
    /** The platform itself. */
    SYSTEM
}

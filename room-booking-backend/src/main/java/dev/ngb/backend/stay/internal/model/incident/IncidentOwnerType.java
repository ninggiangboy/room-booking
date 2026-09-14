package dev.ngb.backend.stay.internal.model.incident;

/**
 * What kind of owner an incident currently has.
 */
public enum IncidentOwnerType {
    /** The host is expected to act. */
    HOST,
    /** The operations team. */
    OPERATIONS,
    /** A named support agent. */
    SUPPORT_AGENT,
    /** A named safety specialist. */
    SAFETY_SPECIALIST,
    /** A queue rather than a person, named separately. */
    QUEUE
}

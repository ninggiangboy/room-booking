package dev.ngb.backend.stay.internal.model.incident;

/**
 * Who raised an incident.
 */
public enum IncidentReporterRole {
    /** The guest. */
    GUEST,
    /** The host. */
    HOST,
    /** A co-host. */
    CO_HOST,
    /** A listing operator. */
    OPERATOR,
    /** A support agent. */
    SUPPORT,
    /** The platform, from a deterministic signal. */
    SYSTEM
}

package dev.ngb.backend.stay.internal.model.incident;

/**
 * What one entry in an incident timeline records.
 */
public enum IncidentEventType {
    /** The incident moved state. */
    STATE_TRANSITION,
    /** Severity changed, with a reason. */
    SEVERITY_CHANGE,
    /** Ownership changed. */
    ASSIGNMENT,
    /** An internal or participant-visible note. */
    NOTE,
    /** Evidence was attached. */
    EVIDENCE_LINKED,
    /** Another domain was asked for something. */
    REMEDY_REQUESTED,
    /** That domain answered. */
    REMEDY_RESULT,
    /** Something was said to a participant. */
    COMMUNICATION,
    /** The response clock started, paused, resumed or stopped. */
    SLO_CLOCK
}

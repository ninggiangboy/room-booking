package dev.ngb.backend.model;

/**
 * Where an incident stands.
 */
public enum IncidentState {
    /** Reported, not yet triaged. */
    OPEN,
    /** Category, severity and route decided. */
    TRIAGED,
    /** Given an owner. */
    ASSIGNED,
    /** Being worked on. */
    RESPONDING,
    /** Immediate impact reduced. Not an entitlement or an admission. */
    MITIGATED,
    /** Waiting on another domain to answer a remedy request. */
    REMEDY_PENDING,
    /** Operationally resolved. */
    RESOLVED,
    /** Communicated and closed; requires a resolution first. */
    CLOSED,
    /** Routed to a dedicated safety owner. */
    SAFETY_ESCALATED,
    /** Linked to another incident, both reports preserved. */
    DUPLICATE,
    /** Ownership moved to support, trust or claims. */
    TRANSFERRED
}

package dev.ngb.backend.model;

/**
 * Kind of principal responsible for an audited action or a published fact.
 *
 * <p>A {@code USER} or {@code OPERATOR} is identified by account ID. A {@code SYSTEM} or
 * {@code PROVIDER} actor has no account and carries a stable reference string instead; the
 * {@code audit_events} check constraint requires one of the two so no row can name nobody.</p>
 */
public enum ActorType {
    /** An end user acting for themselves. */
    USER,
    /** A platform operator acting under delegated authority. */
    OPERATOR,
    /** An internal scheduled or event-driven process. */
    SYSTEM,
    /** An external provider acting through a verified callback. */
    PROVIDER,
    /** An unauthenticated caller. */
    ANONYMOUS
}

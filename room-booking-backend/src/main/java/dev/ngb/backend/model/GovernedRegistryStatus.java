package dev.ngb.backend.model;

/**
 * Lifecycle of a governed definition -- an operator role, a configuration schema, a feature flag or
 * an operational command.
 * <p>Past {@code DRAFT} the definition is frozen: its meaning is what every approval already given
 * was given against, and a different meaning is a new version rather than an edit.</p>
 */
public enum GovernedRegistryStatus {

    /** Still being written; the only state in which it can be edited. */
    DRAFT,

    /** Published and in use. */
    ACTIVE,

    /** Still honoured for what already cites it, but nothing new should. */
    DEPRECATED,

    /** No longer usable; it is not returned to service, a successor is registered instead. */
    RETIRED
}

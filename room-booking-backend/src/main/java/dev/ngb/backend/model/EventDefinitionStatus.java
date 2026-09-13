package dev.ngb.backend.model;

/**
 * Lifecycle of one event contract at one schema version.
 */
public enum EventDefinitionStatus {

    /** Being written; freely editable. */
    DRAFT,

    /** Accepted but not yet produced. */
    REVIEWED,

    /** In production. */
    ACTIVE,

    /** Still readable, with a last production date and an exit plan. */
    DEPRECATED,

    /** No longer accepted; terminal. */
    RETIRED
}

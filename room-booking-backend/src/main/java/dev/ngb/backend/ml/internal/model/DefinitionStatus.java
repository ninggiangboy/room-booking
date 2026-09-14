package dev.ngb.backend.ml.internal.model;

/**
 * Lifecycle of a feature or label definition.
 *
 * <p>Values may be written only against a definition that is in service. Leaving DRAFT freezes the
 * meaning, and RETIRED is terminal.</p>
 */
public enum DefinitionStatus {

    /** Still editable; no values or observations may be written against it. */
    DRAFT,

    /** Reviewed but not yet in service. */
    REVIEWED,

    /** In service. */
    ACTIVE,

    /** Still readable, with a named successor. */
    DEPRECATED,

    /** Withdrawn; terminal. */
    RETIRED
}

package dev.ngb.backend.model;

/**
 * What aspect of a dataset a check tests.
 */
public enum QualityDimension {

    /** How far behind the data is. */
    FRESHNESS,

    /** Whether everything expected arrived. */
    COMPLETENESS,

    /** Whether the declared grain actually holds. */
    UNIQUENESS,

    /** Whether types and required fields are as declared. */
    SCHEMA_VALIDITY,

    /** Whether references resolve and dimension history is valid. */
    REFERENTIAL_INTEGRITY,

    /** Whether values fall in their accepted range. */
    RANGE,

    /** Whether the shape of the data moved unexpectedly. */
    DISTRIBUTION,

    /** Whether event-time ordering or gap behaviour holds. */
    ORDERING,

    /** Whether prohibited fields are absent; always fatal. */
    PRIVACY,

    /** Whether counts and amounts agree with the authoritative source. */
    RECONCILIATION,

    /** Whether the output can be rebuilt from retained inputs and code. */
    REPRODUCIBILITY
}

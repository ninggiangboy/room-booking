package dev.ngb.backend.model;

/**
 * What kind of schema change a contract permits within a version.
 */
public enum SchemaCompatibilityPolicy {

    /** New readers can read old data. */
    BACKWARD,

    /** Old readers can read new data. */
    FORWARD,

    /** Both directions hold. */
    FULL,

    /** No compatibility is promised within the version. */
    NONE
}

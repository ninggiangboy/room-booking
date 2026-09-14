package dev.ngb.backend.analytics.internal.model;

/**
 * Whether and how a published figure may be restated.
 */
public enum RestatementPolicy {

    /** Never restated once published. */
    NONE,

    /** A period stays open for late data until its allowed lateness passes. */
    OPEN_PARTITION,

    /** Restatement publishes a new version rather than reopening. */
    VERSIONED_RESTATEMENT
}

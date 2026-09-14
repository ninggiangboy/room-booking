package dev.ngb.backend.analytics.internal.model.quality;

/**
 * Lifecycle of one quality check definition.
 */
public enum QualityCheckStatus {

    /** Being written. */
    DRAFT,

    /** Evaluated on every run. */
    ACTIVE,

    /** No longer evaluated. */
    RETIRED
}

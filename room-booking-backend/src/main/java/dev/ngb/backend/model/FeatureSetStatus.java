package dev.ngb.backend.model;

/**
 * Lifecycle of a feature set version.
 *
 * <p>Membership can change only while the set is DRAFT, and a set cannot be frozen empty.</p>
 */
public enum FeatureSetStatus {

    /** Membership can still change. */
    DRAFT,

    /** Membership is fixed and a model may be registered against it. */
    FROZEN,

    /** Superseded, but still readable for reproducibility. */
    DEPRECATED,

    /** Withdrawn; terminal. */
    RETIRED
}

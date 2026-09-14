package dev.ngb.backend.ml.internal.model.feature;

/**
 * How much of a feature's stored data an invalidation covers.
 */
public enum FeatureInvalidationScope {

    /** Every stored value of the feature. */
    DEFINITION,

    /** One subject's values. */
    ENTITY,

    /** One partition of the feature's values. */
    PARTITION
}

package dev.ngb.backend.trust.internal.model.feature;

/**
 * The shape of a computed feature value.
 */
public enum FeatureValueType {
    /** A true or false indicator. */
    BOOLEAN,
    /** A named category. */
    CATEGORICAL,
    /** A count over a window. */
    COUNT,
    /** A money amount in minor units. */
    AMOUNT,
    /** A bounded ratio. */
    RATIO,
    /** An elapsed time. */
    DURATION,
    /** A derived score. */
    SCORE
}

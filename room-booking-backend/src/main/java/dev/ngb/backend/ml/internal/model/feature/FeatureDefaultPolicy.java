package dev.ngb.backend.ml.internal.model.feature;

/**
 * What the serving path substitutes when a feature value is absent.
 */
public enum FeatureDefaultPolicy {

    /** Nothing is substituted; the absence is passed through. */
    NONE,

    /** The declared constant is substituted. */
    CONSTANT,

    /** The previous value for the entity is carried forward. */
    LAST_OBSERVED,

    /** The population median is substituted. */
    POPULATION_MEDIAN
}

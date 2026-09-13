package dev.ngb.backend.model;

/**
 * How examples were selected into a training dataset.
 */
public enum SamplingStrategy {

    /** Every eligible example. */
    FULL_POPULATION,

    /** A uniform random sample of the eligible population. */
    UNIFORM,

    /** Sampled to preserve declared strata. */
    STRATIFIED,

    /** Negatives reduced, with weights recorded to compensate. */
    NEGATIVE_DOWNSAMPLED,

    /** Sampled with weights reflecting how examples were selected. */
    IMPORTANCE_WEIGHTED
}

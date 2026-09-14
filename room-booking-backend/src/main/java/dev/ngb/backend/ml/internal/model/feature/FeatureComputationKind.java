package dev.ngb.backend.ml.internal.model.feature;

/**
 * How a feature value is derived from its source.
 */
public enum FeatureComputationKind {

    /** Summed or counted over a window. */
    AGGREGATE,

    /** Computed from other features or facts. */
    DERIVED,

    /** One quantity over another. */
    RATIO,

    /** Produced by an encoder. */
    EMBEDDING,

    /** Copied from the source as it stands. */
    PASSTHROUGH,

    /** Taken from versioned configuration or a taxonomy. */
    EFFECTIVE_DATED
}

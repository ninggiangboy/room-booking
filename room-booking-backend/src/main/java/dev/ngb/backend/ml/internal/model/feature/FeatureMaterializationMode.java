package dev.ngb.backend.ml.internal.model.feature;

/**
 * How and when a feature's values are computed.
 */
public enum FeatureMaterializationMode {

    /** Computed inside the serving request from bounded facts. */
    REQUEST_COMPUTED,

    /** Computed on a schedule, with a stated freshness. */
    BATCH,

    /** Updated from events by an idempotent reducer. */
    INCREMENTAL,

    /** Read from versioned configuration or a taxonomy. */
    STATIC_EFFECTIVE_DATED
}

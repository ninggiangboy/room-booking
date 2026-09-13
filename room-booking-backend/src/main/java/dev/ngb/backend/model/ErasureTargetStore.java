package dev.ngb.backend.model;

/**
 * A derived store that must be told about an erasure directive.
 *
 * <p>Clearing a serving cache while a training dataset keeps the same behaviour is not erasure.</p>
 */
public enum ErasureTargetStore {

    /** Guest preference profile. */
    GUEST_PREFERENCE_PROFILE,

    /** Session intent. */
    SESSION_INTENT,

    /** Discovery events. */
    DISCOVERY_EVENTS,

    /** Serving cache. */
    SERVING_CACHE,

    /** Training dataset. */
    TRAINING_DATASET,

    /** Feature store. */
    FEATURE_STORE,

    /** Experiment exposures. */
    EXPERIMENT_EXPOSURES,

    /** Analytics warehouse. */
    ANALYTICS_WAREHOUSE
}

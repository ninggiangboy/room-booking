package dev.ngb.backend.ml.internal.model.feature;

/**
 * What the serving path does when an invalidation applies.
 */
public enum FeatureServingBehaviour {

    /** Delete the value; the lookup finds nothing. */
    REMOVE,

    /** Return an explicit suppressed state rather than a value. */
    RETURN_SUPPRESSED,

    /** Refuse to serve any model that declares this feature. */
    BLOCK_MODEL
}

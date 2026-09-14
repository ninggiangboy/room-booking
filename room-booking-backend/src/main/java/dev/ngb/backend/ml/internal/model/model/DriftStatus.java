package dev.ngb.backend.ml.internal.model.model;

/**
 * How far the observed input distribution has moved from the trained one.
 */
public enum DriftStatus {

    /** The inputs look like what the model was trained on. */
    NONE,

    /** Measurable movement, worth watching. */
    OBSERVED,

    /** Enough movement that the evaluation no longer describes production. */
    SEVERE
}

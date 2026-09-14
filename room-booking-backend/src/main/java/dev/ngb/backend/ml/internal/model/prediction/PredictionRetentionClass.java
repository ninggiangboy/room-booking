package dev.ngb.backend.ml.internal.model.prediction;

/**
 * How long a prediction record is kept.
 */
public enum PredictionRetentionClass {

    /** The ordinary retention for prediction evidence. */
    STANDARD,

    /** Kept only as long as the immediate evaluation needs it. */
    SHORT,

    /** Kept regardless of expiry, because something requires it. */
    LEGAL_HOLD
}

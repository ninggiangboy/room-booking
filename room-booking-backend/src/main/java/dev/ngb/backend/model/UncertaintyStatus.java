package dev.ngb.backend.model;

/**
 * How far a prediction's inputs sit from the range the model was validated on.
 */
public enum UncertaintyStatus {

    /** The inputs sit inside the range the evaluation covered. */
    WITHIN_VALIDATED_RANGE,

    /** The model is extrapolating, and says so. */
    OUTSIDE_VALIDATED_RANGE,

    /** The model cannot say how reliable this answer is. */
    UNAVAILABLE
}

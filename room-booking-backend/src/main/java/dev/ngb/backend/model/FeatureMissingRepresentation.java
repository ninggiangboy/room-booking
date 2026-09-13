package dev.ngb.backend.model;

/**
 * How the absence of a feature value is represented to the model.
 *
 * <p>Explicit missing is the honest option. The others are imputations that the model must have
 * been trained to expect, or they change the distribution it sees.</p>
 */
public enum FeatureMissingRepresentation {

    /** The model is told the value is absent. */
    EXPLICIT_MISSING,

    /** Absence is presented as zero. */
    ZERO,

    /** Absence is presented as the declared default. */
    DEFAULT_VALUE,

    /** Absence is presented as an out-of-range marker. */
    SENTINEL
}

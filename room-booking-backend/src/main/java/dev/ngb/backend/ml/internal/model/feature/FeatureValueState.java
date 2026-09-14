package dev.ngb.backend.ml.internal.model.feature;

/**
 * Whether a stored feature value is a measurement, an absence or a guess.
 */
public enum FeatureValueState {

    /** Observed and stored. */
    PRESENT,

    /** Known to have no value, recorded explicitly rather than by an absent row. */
    MISSING,

    /** Withheld because an invalidation applies to this subject or partition. */
    SUPPRESSED,

    /** Substituted, with the method that produced it recorded alongside. */
    IMPUTED
}

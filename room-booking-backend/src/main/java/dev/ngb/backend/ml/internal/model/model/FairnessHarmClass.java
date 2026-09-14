package dev.ngb.backend.ml.internal.model.model;

/**
 * What kind of harm a fairness slice is measuring.
 */
public enum FairnessHarmClass {

    /** No harm identified for this slice. */
    NONE,

    /** Unequal chance of being seen or chosen. */
    OPPORTUNITY,

    /** Unequal accuracy. */
    ERROR_RATE,

    /** Unequal price or benefit for equivalent contexts. */
    PRICE_PARITY,

    /** Wrongly restricted or refused. */
    FALSE_RESTRICTION,

    /** New listings or new hosts systematically disadvantaged. */
    NEW_SUPPLY,

    /** Worse quality in some languages than others. */
    LANGUAGE_COVERAGE,

    /** Severe harm; reported even where the cohort is small. */
    SAFETY
}

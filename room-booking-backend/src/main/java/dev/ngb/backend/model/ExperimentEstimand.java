package dev.ngb.backend.model;

/**
 * What quantity an analysis is estimating.
 *
 * <p>Intent to treat measures being assigned; treatment on treated measures being reached. They
 * answer different questions and are not interchangeable.</p>
 */
public enum ExperimentEstimand {

    /** The effect of being assigned, whether or not the treatment was delivered. */
    INTENT_TO_TREAT,

    /** The effect on units the treatment actually reached. */
    TREATMENT_ON_TREATED
}

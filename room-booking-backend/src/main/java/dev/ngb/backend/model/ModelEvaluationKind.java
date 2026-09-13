package dev.ngb.backend.model;

/**
 * What an evaluation run measured.
 */
public enum ModelEvaluationKind {

    /** Held-out performance against a baseline. */
    OFFLINE,

    /** Behaviour on live traffic nobody acted on. */
    SHADOW,

    /** Effects measured per slice, for both sides of the marketplace. */
    FAIRNESS,

    /** Behaviour under malformed input, shift and adversarial pressure. */
    ROBUSTNESS,

    /** How closely predicted probabilities match observed frequencies. */
    CALIBRATION,

    /** How far the observed inputs have moved from the trained ones. */
    DRIFT
}

package dev.ngb.backend.model;

/**
 * What a consumer does when a model cannot answer.
 */
public enum ModelFallbackBehaviour {

    /** Fall back to the rule that worked before the model existed. */
    DETERMINISTIC_BASELINE,

    /** Proceed without any score. */
    NO_PREDICTION,

    /** Route the case to a person instead. */
    HUMAN_REVIEW,

    /** Refuse to proceed. */
    BLOCK
}

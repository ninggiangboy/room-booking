package dev.ngb.backend.discovery.internal.model.ranking;

/**
 * What serves when the model times out or errors.
 */
public enum RankingFallbackMode {

    /** Deterministic baseline. */
    DETERMINISTIC_BASELINE,

    /** Previous model. */
    PREVIOUS_MODEL,

    /** None. */
    NONE
}

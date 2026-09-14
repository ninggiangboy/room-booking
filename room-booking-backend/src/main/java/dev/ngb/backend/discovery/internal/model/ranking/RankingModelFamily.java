package dev.ngb.backend.discovery.internal.model.ranking;

/**
 * The kind of ranker a model version is.
 */
public enum RankingModelFamily {

    /** Deterministic baseline. */
    DETERMINISTIC_BASELINE,

    /** Gradient boosted ranker. */
    GRADIENT_BOOSTED_RANKER,

    /** Linear ranker. */
    LINEAR_RANKER,

    /** Two tower retrieval. */
    TWO_TOWER_RETRIEVAL,

    /** Sequence model. */
    SEQUENCE_MODEL,

    /** Contextual bandit. */
    CONTEXTUAL_BANDIT
}

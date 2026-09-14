package dev.ngb.backend.discovery.internal.model.ranking;

/**
 * Where a ranking policy version stands.
 */
public enum RankingPolicyStatus {

    /** Draft. */
    DRAFT,

    /** Active. */
    ACTIVE,

    /** Superseded. */
    SUPERSEDED,

    /** Retired. */
    RETIRED
}

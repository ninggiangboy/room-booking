package dev.ngb.backend.model;

/**
 * Where a ranking model version stands in the serving lifecycle.
 *
 * <p>A shadow model observes and takes no traffic; a rolled-back model is final, and returning to
 * service means promoting a new version.</p>
 */
public enum RankingModelStatus {

    /** Known to the registry and not serving. */
    REGISTERED,

    /** Scored alongside the live ranker for comparison, taking no traffic. */
    SHADOW,

    /** Approved and awaiting promotion. */
    CANDIDATE,

    /** Serving a share of traffic. */
    ACTIVE,

    /** Pulled from service; returning means promoting a new version. */
    ROLLED_BACK,

    /** No longer part of the serving lifecycle. */
    RETIRED
}

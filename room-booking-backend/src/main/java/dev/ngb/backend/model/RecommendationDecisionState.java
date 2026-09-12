package dev.ngb.backend.model;

/**
 * What became of a price recommendation.
 *
 * <p>This is how the optimizer is evaluated. A recommendation nobody acted on and one a host
 * deliberately rejected are very different signals, and collapsing them into "not applied" would
 * make the model look right whenever it was simply ignored.</p>
 */
public enum RecommendationDecisionState {
    /** Awaiting a decision, and still within its expiry. */
    PENDING,
    /** Taken as offered. */
    ACCEPTED,
    /** Declined; the existing price stands. */
    REJECTED,
    /** Replaced by a different price the host chose instead. */
    OVERRIDDEN,
    /** Lapsed before anyone decided; the demand picture behind it is stale. */
    EXPIRED
}

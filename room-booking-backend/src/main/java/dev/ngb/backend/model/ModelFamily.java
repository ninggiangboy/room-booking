package dev.ngb.backend.model;

/**
 * What kind of model a registered version is, and therefore how it is evaluated.
 */
public enum ModelFamily {

    /** Interprets what a search request is asking for. */
    QUERY_UNDERSTANDING,

    /** Produces similarity candidates. */
    SEMANTIC_RETRIEVAL,

    /** Represents what a guest tends to prefer. */
    GUEST_PREFERENCE,

    /** Orders candidates the domain has already made eligible. */
    LEARNING_TO_RANK,

    /** Extracts aspects and sentiment from written reviews. */
    REVIEW_INTELLIGENCE,

    /** Estimates expected demand or occupancy. */
    DEMAND_FORECAST,

    /** Estimates how demand responds to price. */
    PRICE_ELASTICITY,

    /** Estimates the incremental effect of an offer. */
    PROMOTION_UPLIFT,

    /** Estimates fraud, chargeback, cancellation or damage likelihood. */
    RISK_SCORING,

    /** Routes, triages or drafts for support. */
    SUPPORT_ASSISTANCE,

    /** Estimates channel, timing or engagement. */
    MESSAGING_OPTIMIZATION,

    /** Classifies content or detects duplicates. */
    CONTENT_CLASSIFICATION
}

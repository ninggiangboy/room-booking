package dev.ngb.backend.trust.internal.model.feature;

/**
 * What a feature is computed about.
 */
public enum FeatureEntityScope {
    /** One account holder. */
    ACCOUNT,
    /** One host organization. */
    ORGANIZATION,
    /** One listing. */
    LISTING,
    /** One booking. */
    BOOKING,
    /** One device reference. */
    DEVICE,
    /** One network observation. */
    NETWORK,
    /** One payment instrument or payout destination. */
    INSTRUMENT,
    /** One content item. */
    CONTENT,
    /** One protected action request. */
    ACTION;
}

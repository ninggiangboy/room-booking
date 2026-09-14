package dev.ngb.backend.analytics.internal.model.metric;

/**
 * What a metric measures in.
 *
 * <p>Money is integer minor units. No floating-point value is used as transactional money.</p>
 */
public enum MeasureUnit {

    /** A numerator over a denominator. */
    RATIO,

    /** A plain count. */
    COUNT,

    /** Integer minor units of a currency. */
    MONEY_MINOR,

    /** A duration in seconds. */
    DURATION_SECONDS,

    /** A bounded score that is neither money nor a count. */
    SCORE
}

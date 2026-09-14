package dev.ngb.backend.analytics.internal.model.metric;

/**
 * Which part of the marketplace a metric describes.
 */
public enum MetricFamily {

    /** Search, results, clicks and the path to a quote. */
    DISCOVERY,

    /** Completion, satisfaction and what the stay was actually like. */
    STAY_VALUE,

    /** What a guest pays, is refunded, and perceives as value. */
    GUEST_ECONOMICS,

    /** Nights, occupancy, rate and what a host is entitled to. */
    HOST_ECONOMICS,

    /** Recognised revenue and contribution, on finance-owned definitions. */
    PLATFORM_ECONOMICS,

    /** Latency, errors, stale data and recovery. */
    RELIABILITY,

    /** Incidents, escalations, remedies and recurrence. */
    SAFETY_SUPPORT,

    /** Liquidity, concentration, new-listing exposure and repeat supply and demand. */
    MARKETPLACE
}

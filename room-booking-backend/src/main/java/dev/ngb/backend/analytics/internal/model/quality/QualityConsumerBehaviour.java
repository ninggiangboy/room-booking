package dev.ngb.backend.analytics.internal.model.quality;

/**
 * What a consumer does when a check fails.
 */
public enum QualityConsumerBehaviour {

    /** Do not read the dataset at all. */
    BLOCK,

    /** Use the previous good version. */
    FALLBACK,

    /** Read, but hold the affected records aside. */
    QUARANTINE,

    /** Proceed; only available for warning-grade checks. */
    ACCEPT
}

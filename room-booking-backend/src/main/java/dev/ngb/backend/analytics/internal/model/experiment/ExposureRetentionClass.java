package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * How long an exposure record is kept.
 */
public enum ExposureRetentionClass {

    /** The retention the experiment declares. */
    STANDARD,

    /** A shorter horizon for high-volume surfaces. */
    SHORT,

    /** Retained beyond its normal horizon because a legal hold applies. */
    LEGAL_HOLD
}

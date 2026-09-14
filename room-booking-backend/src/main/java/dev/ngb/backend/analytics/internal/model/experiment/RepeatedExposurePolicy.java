package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * How repeated exposures of one unit are counted.
 *
 * <p>Raw views, views deduplicated per request and views deduplicated per unit per day may all be
 * valid measures, but they cannot share one ambiguous name.</p>
 */
public enum RepeatedExposurePolicy {

    /** Only the first exposure of a unit counts. */
    FIRST_ONLY,

    /** Deduplicated within one request. */
    PER_REQUEST,

    /** Deduplicated per unit per day. */
    PER_UNIT_DAY,

    /** Every exposure counts. */
    RAW
}

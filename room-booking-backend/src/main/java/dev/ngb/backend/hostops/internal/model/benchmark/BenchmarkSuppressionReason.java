package dev.ngb.backend.hostops.internal.model.benchmark;

/**
 * Why a market benchmark carries no numbers. The reason recorded has to be the one that actually
 * holds, so a quality failure cannot be reported to hosts as a privacy control.
 */
public enum BenchmarkSuppressionReason {

    /** Fewer distinct hosts than the cohort floor, so a contributor could subtract themselves out of the aggregate. */
    TOO_FEW_CONTRIBUTORS,

    /** Fewer observations than the cohort floor. */
    TOO_FEW_OBSERVATIONS,

    /** One host supplied more of the cohort than its ceiling allows. */
    CONTRIBUTOR_CONCENTRATION,

    /** The input data did not meet the quality bar the metric requires. */
    QUALITY_GATE_FAILED,

    /** The cohort itself has been withdrawn. */
    COHORT_RETIRED
}

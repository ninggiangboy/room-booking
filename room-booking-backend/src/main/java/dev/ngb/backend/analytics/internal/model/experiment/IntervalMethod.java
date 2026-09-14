package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * How the interval around an estimate is computed.
 */
public enum IntervalMethod {

    /** Large-sample normal interval. */
    NORMAL_APPROXIMATION,

    /** Resampled interval. */
    BOOTSTRAP,

    /** Interval for a ratio derived from its components. */
    DELTA_METHOD,

    /** Credible interval from a posterior. */
    BAYESIAN
}

package dev.ngb.backend.model;

/**
 * What adjustment the analysis applies to tighten its intervals.
 *
 * <p>Fixed in advance; choosing it after seeing the estimate is choosing the estimate.</p>
 */
public enum VarianceReduction {

    /** No adjustment. */
    NONE,

    /** Adjustment using pre-period outcomes. */
    CUPED,

    /** Estimation within strata fixed in advance. */
    STRATIFICATION,

    /** Covariate adjustment fixed in advance. */
    REGRESSION_ADJUSTMENT
}

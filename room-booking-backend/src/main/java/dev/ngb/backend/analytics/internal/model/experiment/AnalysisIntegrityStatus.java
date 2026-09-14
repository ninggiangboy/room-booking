package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * Standing of the automated integrity checks on one analysis run.
 */
public enum AnalysisIntegrityStatus {

    /** All integrity checks held. */
    PASS,

    /** Something is off but interpretable. */
    WARN,

    /** Interpretation is not supported. */
    FAIL
}

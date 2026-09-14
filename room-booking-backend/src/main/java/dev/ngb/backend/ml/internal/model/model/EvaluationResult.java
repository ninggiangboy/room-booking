package dev.ngb.backend.ml.internal.model.model;

/**
 * Whether an evaluation run passed, warned or failed.
 */
public enum EvaluationResult {

    /** Met its thresholds. */
    PASS,

    /** Met them with a finding somebody has to read. */
    WARN,

    /** Did not meet them; a promotion may not rest on it. */
    FAIL
}

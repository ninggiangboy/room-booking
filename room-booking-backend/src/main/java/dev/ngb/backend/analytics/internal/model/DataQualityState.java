package dev.ngb.backend.analytics.internal.model;

/**
 * Standing of a run, a check, or a published number.
 *
 * <p>UNKNOWN is a real state and not a synonym for PASS: a check that could not be evaluated has
 * told you nothing.</p>
 */
public enum DataQualityState {

    /** Met its objective. */
    PASS,

    /** Outside target but within tolerance. */
    WARN,

    /** Did not meet its objective. */
    FAIL,

    /** Could not be evaluated, which is not the same as having passed. */
    UNKNOWN
}

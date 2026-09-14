package dev.ngb.backend.review.internal.model.aspect;

/**
 * How far one attempt to read aspects out of a review has got.
 */
public enum ExtractionRunState {
    /** Queued. */
    PLANNED,
    /** Claimed by a worker under a lease. */
    RUNNING,
    /** Produced a validated output, once, for this canonical identity. */
    SUCCEEDED,
    /** Did not produce an output; the error class is recorded. */
    FAILED,
    /** Produced something that failed validation. */
    REJECTED,
    /** Abandoned before completion. */
    CANCELLED
}

package dev.ngb.backend.model;

/**
 * Whether a suppressed destination is waiting for a human look.
 */
public enum ContactHealthReviewState {
    /** No review applies. */
    NOT_REQUIRED,
    /** Queued for review. */
    PENDING,
    /** Reviewed and resolved. */
    CLEARED
}

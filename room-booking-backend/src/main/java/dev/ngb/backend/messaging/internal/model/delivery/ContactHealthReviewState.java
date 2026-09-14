package dev.ngb.backend.messaging.internal.model.delivery;

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

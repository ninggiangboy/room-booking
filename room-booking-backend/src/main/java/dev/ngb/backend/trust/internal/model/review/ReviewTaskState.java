package dev.ngb.backend.trust.internal.model.review;

/**
 * Where a review task stands.
 *
 * <p>Claims are leases with a fencing token rather than assignments. A stale worker writing back an
 * older token is refused, and a closed task cannot be reopened.</p>
 */
public enum ReviewTaskState {
    /** Waiting to be claimed. */
    QUEUED,
    /** Held under a lease. */
    CLAIMED,
    /** Actively being worked. */
    IN_REVIEW,
    /** Closed by a terminal reviewer action. */
    DECIDED,
    /** Passed to another queue. */
    ESCALATED,
    /** Closed without a decision. */
    CANCELLED;
}

package dev.ngb.backend.model;

/**
 * How far the decision to tell somebody something has progressed.
 */
public enum NotificationIntentState {
    /** Created from a committed fact, not yet evaluated for consent and timing. */
    PENDING,
    /** Waiting for its send instant. */
    SCHEDULED,
    /** Consent, preference or destination health stopped it; the reason is recorded. */
    SUPPRESSED,
    /** Eligible for dispatch now. */
    READY,
    /** Claimed by a worker under a lease. */
    DISPATCHING,
    /** At least one attempt reached a satisfactory outcome. */
    COMPLETED,
    /** Its usefulness passed before it was sent. */
    EXPIRED,
    /** No route succeeded and no further retry applies. */
    FAILED,
    /** A changed domain fact produced a replacement intent. */
    SUPERSEDED
}

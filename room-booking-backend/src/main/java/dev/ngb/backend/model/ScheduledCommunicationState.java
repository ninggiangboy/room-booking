package dev.ngb.backend.model;

/**
 * How far a scheduled reminder has got.
 */
public enum ScheduledCommunicationState {
    /** Waiting for its resolved instant. */
    SCHEDULED,
    /** Held by a worker under a lease. */
    CLAIMED,
    /** It created an intent, which the row names. */
    FIRED,
    /** Revalidation refused it; the reason is recorded. */
    DISCARDED,
    /** A newer anchor version replaced it. */
    SUPERSEDED,
    /** Its usefulness passed before it fired. */
    EXPIRED
}

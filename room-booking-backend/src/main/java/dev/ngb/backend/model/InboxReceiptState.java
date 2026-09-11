package dev.ngb.backend.model;

/**
 * Processing state of one consumer's receipt for one event.
 *
 * <p>The receipt is written in the same transaction as the consumer's durable effect, so a
 * {@code COMPLETED} receipt is proof that the effect happened exactly once.</p>
 */
public enum InboxReceiptState {
    /** The event has been seen but not yet processed. */
    PENDING,
    /** Claimed under a lease by one consumer instance. */
    PROCESSING,
    /** The durable effect was applied and committed with this receipt. */
    COMPLETED,
    /** Processing failed with a classified failure; the event remains eligible for retry. */
    FAILED,
    /** Deliberately not applicable to this consumer; recorded so it is never reconsidered. */
    SKIPPED
}

package dev.ngb.backend.model;

/**
 * Progress of one inbound provider event.
 *
 * <p>The row is written before any business effect is applied, so a provider can be acknowledged
 * quickly and the work retried without asking for the event again.</p>
 */
public enum WebhookDeliveryState {
    /** Stored, not yet processed. */
    RECEIVED,
    /** Claimed by a worker under a lease. */
    PROCESSING,
    /** Applied, in the same transaction that recorded the effect. */
    PROCESSED,
    /** Processing failed in a way worth trying again. */
    RETRYABLE_FAILED,
    /** Abandoned after repeated failure, or never trustworthy. */
    DEAD_LETTER,
    /** A recognised delivery of an event type the platform does not act on. */
    IGNORED_UNSUPPORTED
}

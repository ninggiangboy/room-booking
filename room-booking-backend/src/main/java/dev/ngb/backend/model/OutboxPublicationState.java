package dev.ngb.backend.model;

/**
 * Publication progress of an outbox fact.
 *
 * <p>This is operational state, not event meaning: the payload of an {@code outbox_events} row is
 * immutable after commit, and changing the publication state never changes what happened.</p>
 */
public enum OutboxPublicationState {
    /** Committed and waiting to be claimed by a publisher. */
    PENDING,
    /** Claimed under a lease by one publisher instance. */
    PUBLISHING,
    /** Handed to the transport successfully. */
    PUBLISHED,
    /** The last attempt failed; the row is eligible for another attempt at {@code availableAt}. */
    FAILED,
    /** Deliberately abandoned by an operator; no further attempt is made. */
    DISCARDED
}

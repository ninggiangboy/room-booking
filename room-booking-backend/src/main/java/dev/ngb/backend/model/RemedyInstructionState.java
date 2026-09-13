package dev.ngb.backend.model;

/**
 * Where a downstream instruction stands.
 *
 * <p>{@code UNKNOWN} is a first-class state, not a failure: the same idempotency key is queried rather
 * than a second identity being invented to make a screen move.</p>
 */
public enum RemedyInstructionState {

    /** Prepared. */
    PREPARED,

    /** Dispatched. */
    DISPATCHED,

    /** Accepted. */
    ACCEPTED,

    /** Rejected. */
    REJECTED,

    /** Pending. */
    PENDING,

    /** Unknown. */
    UNKNOWN,

    /** Succeeded. */
    SUCCEEDED,

    /** Partial. */
    PARTIAL,

    /** Failed. */
    FAILED,

    /** Cancelled. */
    CANCELLED
}

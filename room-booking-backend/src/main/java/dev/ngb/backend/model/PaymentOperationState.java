package dev.ngb.backend.model;

/**
 * Progress of one external side effect.
 *
 * <p>{@link #FAILED} is a claim that the intended effect did not and cannot occur, and it requires
 * evidence. A transport error, an HTTP timeout, a malformed response after submission, and a crash
 * after send are all {@link #UNKNOWN} until a query or reconciliation resolves them.</p>
 *
 * <p>Only {@link #PLANNED} and {@link #READY_TO_SUBMIT} can be cancelled locally, because they have
 * not crossed the submission fence. The database enforces that directly.</p>
 */
public enum PaymentOperationState {
    /** Created, not yet released to the executor. */
    PLANNED,
    /** Committed and waiting for a worker to claim it. */
    READY_TO_SUBMIT,
    /** Crossing to the provider right now. */
    SUBMITTING,
    /** The provider accepted asynchronous work. */
    PENDING,
    /** The provider is waiting on the guest. */
    REQUIRES_ACTION,
    /** Verified evidence shows the effect occurred. */
    SUCCEEDED,
    /** Verified evidence shows the effect did not and cannot occur. */
    FAILED,
    /** The request may have taken effect; resolve by query or reconciliation. */
    UNKNOWN,
    /** Resolved as no longer possible once its deadline passed. */
    EXPIRED,
    /** Withdrawn while it had not yet reached the provider. */
    CANCELLED_BEFORE_SUBMISSION
}

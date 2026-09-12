package dev.ngb.backend.model;

/**
 * How far a transfer to a host has got.
 *
 * <p>{@code PAID} means the approved verified finality condition was met. A provider saying
 * "submitted" or "processed" is not that, and mapping it optimistically is how a payout is reported
 * as complete before the money exists in the host's account.</p>
 *
 * <p>An instruction that reached the provider can never be {@code CANCELLED}; the database refuses
 * it. A timeout leaves it in {@code SUBMITTED}, which must be queried rather than retried.</p>
 */
public enum PayoutInstructionState {
    /** Being assembled; its items may still change. */
    DRAFT,
    /** Its payable allocations are claimed and its amount is fixed. */
    ITEMS_RESERVED,
    /** Revalidated and waiting for a worker. */
    READY_TO_SUBMIT,
    /** A worker holds the lease and is about to call the provider. */
    SUBMITTING,
    /** The provider has it; the outcome is not yet known. */
    SUBMITTED,
    /** The provider accepted it and the transfer is moving. */
    IN_TRANSIT,
    /** The verified finality condition was met. */
    PAID,
    /** Terminally failed, with a classification saying what kind. */
    FAILED,
    /** Abandoned before it ever reached the provider. */
    CANCELLED,
    /** Paid and then returned by the receiving bank. */
    RETURNED,
    /** The return has been posted as a recovery against the host. */
    RECOVERY_POSTED,
    /** Escalated to a person; its settlement or failure history stands. */
    MANUAL_REVIEW
}

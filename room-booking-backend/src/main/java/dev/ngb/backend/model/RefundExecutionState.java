package dev.ngb.backend.model;

/**
 * Progress of carrying out an approved refund.
 *
 * <p>A cancelled booking can sit here for days without its inventory waiting: releasing nights must
 * never depend on a provider completing a transfer. Guest messaging distinguishes approved from
 * submitted from received, because bank timing is not the platform's to control.</p>
 */
public enum RefundExecutionState {
    /** An instruction exists; nothing reserved yet. */
    APPROVED,
    /** Refundable capture amount is held against this instruction. */
    RESERVED,
    /** A refund operation is crossing to the provider. */
    SUBMITTING,
    /** The provider accepted the refund and has not completed it. */
    PENDING,
    /** The whole approved amount was returned. */
    SUCCEEDED,
    /** Some child operations succeeded and the rest need escalation. */
    PARTIALLY_SUCCEEDED,
    /** Failed in a way worth trying again. */
    FAILED_RETRYABLE,
    /** Failed in a way that needs a different remedy. */
    FAILED_FINAL,
    /** Submitted with no proven outcome; the reservation stays held. */
    UNKNOWN
}

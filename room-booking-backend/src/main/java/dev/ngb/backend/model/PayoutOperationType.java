package dev.ngb.backend.model;

/**
 * What a payout operation asks the provider to do.
 */
public enum PayoutOperationType {
    /** Create the transfer. */
    SUBMIT,
    /** Ask what happened, without moving money. */
    QUERY,
    /** Ask the provider to stop a transfer it has not sent. */
    CANCEL,
    /** Re-present the same transfer under the same idempotency key. */
    RETRY
}

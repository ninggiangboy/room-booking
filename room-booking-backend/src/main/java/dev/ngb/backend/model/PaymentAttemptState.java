package dev.ngb.backend.model;

/**
 * Progress of one guest journey toward satisfying an obligation.
 *
 * <p>A projection for orchestration and the interface. The operations are the auditable record of
 * what actually succeeded; refund and dispute facts are deliberately not compressed into this
 * column, because they can be true at the same time as a capture.</p>
 */
public enum PaymentAttemptState {
    /** Recorded, not yet submitted. */
    CREATED,
    /** A first operation is crossing to the provider. */
    SUBMITTING,
    /** Waiting on the guest, with a deadline. */
    REQUIRES_ACTION,
    /** Submitted and unresolved. */
    PROCESSING,
    /** Funds reserved at the provider. */
    AUTHORIZED,
    /** Some of the requested amount has been captured. */
    PARTIALLY_CAPTURED,
    /** The whole requested amount has been captured. */
    CAPTURED,
    /** Captured and partly returned. */
    PARTIALLY_REFUNDED,
    /** Captured and returned in full. */
    REFUNDED,
    /** An authorisation was released without capture. */
    VOIDED,
    /** A reservation lapsed before it was captured. */
    AUTHORIZATION_EXPIRED,
    /** Ended with evidence that no money moved. */
    FAILED,
    /** Abandoned before submission. */
    CANCELLED,
    /** Ended because its deadline passed. */
    EXPIRED
}

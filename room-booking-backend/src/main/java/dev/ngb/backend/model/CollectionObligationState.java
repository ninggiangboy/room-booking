package dev.ngb.backend.model;

/**
 * Progress of what a guest owes.
 *
 * <p>{@code FAILED} is deliberately absent. A declined card describes an attempt, not the
 * obligation: the guest may still satisfy it with another method. An obligation closes only by
 * being paid, by its contractual deadline, or by an owner command.</p>
 */
public enum CollectionObligationState {
    /** Nothing collected yet. */
    OPEN,
    /** Waiting on the guest to complete an authentication or approval. */
    ACTION_REQUIRED,
    /** A submitted operation has not yet resolved. */
    PROCESSING,
    /** Funds reserved at the provider but not yet captured. */
    AUTHORIZED,
    /** Some of the amount has been captured. */
    PARTIALLY_PAID,
    /** The whole amount has been captured. */
    PAID,
    /** Collected in full and partly returned. */
    PARTIALLY_REFUNDED,
    /** Collected and returned in full. */
    REFUNDED,
    /** An authorisation was released without capture. */
    VOIDED,
    /** Closed by an owner command before it was satisfied. */
    CANCELLED,
    /** Closed by its contractual deadline before it was satisfied. */
    EXPIRED
}

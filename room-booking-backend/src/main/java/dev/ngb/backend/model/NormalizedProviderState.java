package dev.ngb.backend.model;

/**
 * What a provider said, expressed in the platform's own vocabulary.
 *
 * <p>The adapter maps provider-specific status strings onto these before any state is derived, so
 * the reducer never has to know which provider the evidence came from.</p>
 */
public enum NormalizedProviderState {
    /** Funds reserved. */
    AUTHORIZED,
    /** Funds collected in full. */
    CAPTURED,
    /** Part of the reservation collected. */
    PARTIALLY_CAPTURED,
    /** Reservation released. */
    VOIDED,
    /** Captured funds returned in full. */
    REFUNDED,
    /** Part of the captured funds returned. */
    PARTIALLY_REFUNDED,
    /** The instrument was refused. */
    DECLINED,
    /** The action did not and cannot occur. */
    FAILED,
    /** Accepted and unresolved. */
    PENDING,
    /** Waiting on the customer. */
    REQUIRES_ACTION,
    /** No longer actionable at the provider. */
    EXPIRED,
    /** Contested by the cardholder or their bank. */
    DISPUTED,
    /** The provider's answer did not determine an outcome. */
    UNKNOWN
}

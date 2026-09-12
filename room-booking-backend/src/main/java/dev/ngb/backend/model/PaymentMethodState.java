package dev.ngb.backend.model;

/**
 * Usability of a stored payment-method reference.
 *
 * <p>Revocation disables new operations. It does not delete the reference, because historic
 * transactions and open disputes still have to be explainable.</p>
 */
public enum PaymentMethodState {
    /** Usable for new operations. */
    ACTIVE,
    /** Past its validity; the provider will refuse it. */
    EXPIRED,
    /** Withdrawn by the guest or the platform. */
    REVOKED,
    /** Superseded by a newer reference for the same instrument. */
    REPLACED
}

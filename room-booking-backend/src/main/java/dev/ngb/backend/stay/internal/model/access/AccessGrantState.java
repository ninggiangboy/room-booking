package dev.ngb.backend.stay.internal.model.access;

/**
 * Where a platform access entitlement stands.
 *
 * <p>{@code UNKNOWN} is a real answer: an unresolved provider outcome means the credential must be
 * treated as possibly live. Revocation is terminal, enforced by trigger.</p>
 */
public enum AccessGrantState {
    /** Being prepared. */
    DRAFT,
    /** Authorized, not yet fulfilled. */
    ELIGIBLE,
    /** A provider operation is in flight. */
    PROVISIONING,
    /** Fulfilled and able to open the door. */
    ACTIVE,
    /** Validity has passed. */
    EXPIRED,
    /** The provider outcome is unresolved. */
    UNKNOWN,
    /** Withdrawal has been requested. */
    REVOKING,
    /** Withdrawal is confirmed. */
    REVOKED,
    /** Fulfilment failed and a fallback is needed. */
    FAILED
}

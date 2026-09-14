package dev.ngb.backend.growth.internal.model.storedvalue;

/**
 * Where one lot of stored value stands.
 *
 * <p>Expiry belongs to the lot rather than to the balance, so the platform can always say which
 * promise expired and which one was spent.</p>
 */
public enum StoredValueLotState {

    /** Still has value and is still spendable. */
    ACTIVE,

    /** Spent down to nothing. */
    EXHAUSTED,

    /** Past its own expiry, whatever was left. */
    EXPIRED,

    /** Withdrawn, for a recorded reason. */
    REVOKED
}

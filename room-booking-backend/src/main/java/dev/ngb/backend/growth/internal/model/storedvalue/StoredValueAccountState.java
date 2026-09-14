package dev.ngb.backend.growth.internal.model.storedvalue;

/**
 * Where a stored-value balance stands.
 */
public enum StoredValueAccountState {

    /** Spendable. */
    ACTIVE,

    /** Held pending a decision; it may be released but not spent. */
    FROZEN,

    /** Finished, which requires the balance to be empty. */
    CLOSED
}

package dev.ngb.backend.model;

/**
 * Which entry from the allowlisted adjustment catalog is being proposed.
 *
 * <p>There is no free-form posting endpoint. The type decides which accounts and dimensions are
 * legal, what the amount bounds are, how many approvals are needed, and which posting rule runs.</p>
 */
public enum FinanceAdjustmentType {
    /** Reverses a fee that was charged twice. */
    DUPLICATE_FEE_CORRECTION,
    /** Corrects an estimated provider fee to the amount actually charged. */
    PROVIDER_FEE_TRUE_UP,
    /** Funds an approved goodwill gesture. */
    GOODWILL_FUNDING,
    /** Clears a rounding remainder to its designated account. */
    ROUNDING_CORRECTION,
    /** Records the collection of an amount a host owes. */
    RECOVERY,
    /** Recognises that a receivable will not be recovered. */
    WRITE_OFF,
    /** Moves an amount between accounts without changing the total. */
    RECLASSIFICATION,
    /** Records a foreign-exchange gain or loss. */
    FX_DIFFERENCE
}

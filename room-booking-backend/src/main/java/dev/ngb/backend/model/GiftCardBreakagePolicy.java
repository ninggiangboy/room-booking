package dev.ngb.backend.model;

/**
 * What happens to value on a gift card that is never spent.
 *
 * <p>Several markets forbid gift-card expiry outright, so the policy and the expiry column have
 * to agree: a card issued under {@code NEVER_EXPIRES} may not quietly acquire a date.</p>
 */
public enum GiftCardBreakagePolicy {

    /** The value stays spendable indefinitely, and the card carries no expiry. */
    NEVER_EXPIRES,

    /** Unspent value becomes revenue once the card expires. */
    EXPIRES_TO_BREAKAGE,

    /** Unspent value is returned rather than kept. */
    EXPIRES_TO_REFUND
}

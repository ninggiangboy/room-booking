package dev.ngb.backend.model;

/**
 * Whether a payout item still holds its payable allocation.
 *
 * <p>A partial unique index lets at most one live item hold any allocation. {@link #RELEASED} frees
 * the allocation for a later payout; {@link #RETURNED} does not, because the money already left and
 * comes back through a recovery instead.</p>
 */
public enum PayoutItemState {
    /** Claiming the allocation for an instruction that has not settled. */
    RESERVED,
    /** Paid out. */
    SETTLED,
    /** Given back to the balance without being paid. */
    RELEASED,
    /** Paid and then returned; a recovery owns what happens next. */
    RETURNED
}

package dev.ngb.backend.model;

/**
 * Where a single use of a promotion stands.
 *
 * <p>Budget is committed at {@code RESERVED}, not at {@code REDEEMED}. Every open quote is an
 * unrecorded commitment otherwise, and a campaign debited only on confirmation will overspend by
 * however much sits in checkout at the moment it is measured.</p>
 */
public enum PromotionRedemptionState {
    /** Committed against the budget while a quote is open. */
    RESERVED,
    /** Actually granted on a confirmed booking. */
    REDEEMED,
    /** Returned to the budget because the quote lapsed or was abandoned. */
    RELEASED,
    /** Undone after the fact, typically with a cancellation or correction. */
    REVERSED
}

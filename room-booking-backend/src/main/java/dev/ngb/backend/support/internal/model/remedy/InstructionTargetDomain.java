package dev.ngb.backend.support.internal.model.remedy;

/**
 * Which domain owns the effect an instruction asks for.
 */
public enum InstructionTargetDomain {

    /** Payment. */
    PAYMENT,

    /** Ledger. */
    LEDGER,

    /** Payout. */
    PAYOUT,

    /** Booking. */
    BOOKING,

    /** Cancellation. */
    CANCELLATION,

    /** Inventory. */
    INVENTORY,

    /** Pricing. */
    PRICING,

    /** Identity. */
    IDENTITY,

    /** Risk. */
    RISK,

    /** Supply. */
    SUPPLY,

    /** Stay operations. */
    STAY_OPERATIONS,

    /** Communication. */
    COMMUNICATION
}

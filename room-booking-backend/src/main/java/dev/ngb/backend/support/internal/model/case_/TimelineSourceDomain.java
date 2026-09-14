package dev.ngb.backend.support.internal.model.case_;

import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;

/**
 * The source domain of {@code case_timeline_entries}.
 */
public enum TimelineSourceDomain {

    /** Support. */
    SUPPORT,

    /** Booking. */
    BOOKING,

    /** Payment. */
    PAYMENT,

    /** Ledger. */
    LEDGER,

    /** Payout. */
    PAYOUT,

    /** Cancellation. */
    CANCELLATION,

    /** Inventory. */
    INVENTORY,

    /** Pricing. */
    PRICING,

    /** Stay operations. */
    STAY_OPERATIONS,

    /** Risk. */
    RISK,

    /** Review. */
    REVIEW,

    /** Identity. */
    IDENTITY,

    /** Supply. */
    SUPPLY,

    /** Communication. */
    COMMUNICATION,

    /** External. */
    EXTERNAL
}

package dev.ngb.backend.support.internal.model.case_;

import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;

/**
 * The related domain of {@code case_relationships}.
 */
public enum CaseRelatedDomain {

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

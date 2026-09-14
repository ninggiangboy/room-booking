package dev.ngb.backend.growth.internal.model.loyalty;

/**
 * What produced one accrual toward a loyalty tier.
 */
public enum LoyaltyQualifyingEventKind {

    /** A stay that actually happened. */
    STAY_COMPLETED,

    /** A booking that was confirmed. */
    BOOKING_CONFIRMED,

    /** Activity with a partner that the terms count. */
    PARTNER_ACTIVITY,

    /** Credit added or removed by hand, with somebody answerable for it. */
    MANUAL_ADJUSTMENT
}

package dev.ngb.backend.hostops.internal.model.forecast;

import dev.ngb.backend.pricing.internal.model.promotion.Promotion;
import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.pricing.internal.model.promotion.Promotion;

/**
 * The layers that can set a calendar value, from the rate plan underneath to the booking claim on
 * top. Which one won, and which one it beat, is what the host is owed when they ask why a night
 * says what it says.
 */
public enum CalendarSourceLayer {

    /** System default. */
    SYSTEM_DEFAULT,

    /** Rate plan. */
    RATE_PLAN,

    /** Price rule. */
    PRICE_RULE,

    /** Manual override. */
    MANUAL_OVERRIDE,

    /** Recommendation applied. */
    RECOMMENDATION_APPLIED,

    /** Promotion. */
    PROMOTION,

    /** Bulk edit. */
    BULK_EDIT,

    /** Channel sync. */
    CHANNEL_SYNC,

    /** Inventory block. */
    INVENTORY_BLOCK,

    /** Inventory hold. */
    INVENTORY_HOLD,

    /** Booking claim. */
    BOOKING_CLAIM
}

package dev.ngb.backend.stay.internal.model.incident;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.supply.internal.model.listing.Listing;

/**
 * Which domain is being asked to do something.
 */
public enum RemedyTargetDomain {
    /** Booking lifecycle. */
    BOOKING,
    /** Cancellation, modification and refund. */
    CANCELLATION,
    /** Payment orchestration. */
    PAYMENTS,
    /** Ledger and payout. */
    FINANCE,
    /** Availability and calendar. */
    INVENTORY,
    /** Listing publication. */
    LISTING,
    /** Messaging and notifications. */
    MESSAGING,
    /** Trust and safety. */
    TRUST,
    /** Support cases. */
    SUPPORT,
    /** Damage claims. */
    CLAIMS
}

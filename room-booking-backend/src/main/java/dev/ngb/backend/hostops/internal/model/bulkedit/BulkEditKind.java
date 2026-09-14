package dev.ngb.backend.hostops.internal.model.bulkedit;

import dev.ngb.backend.supply.internal.model.listing.Listing;

import dev.ngb.backend.supply.internal.model.listing.Listing;

/**
 * What a bulk edit changes.
 */
public enum BulkEditKind {

    /** Availability. */
    AVAILABILITY,

    /** Nightly price. */
    NIGHTLY_PRICE,

    /** Stay restriction. */
    STAY_RESTRICTION,

    /** Rate plan. */
    RATE_PLAN,

    /** Listing content. */
    LISTING_CONTENT
}

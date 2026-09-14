package dev.ngb.backend.hostops.internal.model.bulkedit;

import dev.ngb.backend.supply.internal.model.listing.Listing;

import dev.ngb.backend.supply.internal.model.listing.Listing;

/**
 * What kind of thing one target of a bulk edit is.
 */
public enum BulkEditTargetKind {

    /** Availability day. */
    AVAILABILITY_DAY,

    /** Listing. */
    LISTING,

    /** Rate plan. */
    RATE_PLAN,

    /** Accommodation type. */
    ACCOMMODATION_TYPE
}

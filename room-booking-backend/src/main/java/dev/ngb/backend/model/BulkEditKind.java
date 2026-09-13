package dev.ngb.backend.model;

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

package dev.ngb.backend.model;

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

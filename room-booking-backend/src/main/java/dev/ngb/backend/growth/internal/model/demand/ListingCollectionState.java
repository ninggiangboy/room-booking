package dev.ngb.backend.growth.internal.model.demand;

/**
 * Where a wish list stands.
 */
public enum ListingCollectionState {

    /** In use. */
    ACTIVE,

    /** Kept but out of the way. */
    ARCHIVED,

    /** Removed by the guest. */
    DELETED
}

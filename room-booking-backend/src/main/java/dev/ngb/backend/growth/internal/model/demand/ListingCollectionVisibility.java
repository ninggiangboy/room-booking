package dev.ngb.backend.growth.internal.model.demand;

/**
 * Who can see a guest’s wish list.
 */
public enum ListingCollectionVisibility {

    /** Only the guest who made it. */
    PRIVATE,

    /** Anybody holding the link. */
    LINK_SHARED,

    /** Anybody. */
    PUBLIC
}

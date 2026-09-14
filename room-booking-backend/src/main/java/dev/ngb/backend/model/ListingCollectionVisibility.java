package dev.ngb.backend.model;

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

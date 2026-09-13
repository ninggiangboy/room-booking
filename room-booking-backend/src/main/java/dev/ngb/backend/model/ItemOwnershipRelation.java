package dev.ngb.backend.model;

/**
 * The ownership relation of {@code damage_claim_items}.
 */
public enum ItemOwnershipRelation {

    /** Listing property. */
    LISTING_PROPERTY,

    /** Host personal. */
    HOST_PERSONAL,

    /** Guest personal. */
    GUEST_PERSONAL,

    /** Third party. */
    THIRD_PARTY,

    /** Shared building. */
    SHARED_BUILDING
}

package dev.ngb.backend.model;

/**
 * How the candidate set for a search was framed.
 */
public enum DiscoverySearchMode {

    /** Destination. */
    DESTINATION,

    /** Map viewport. */
    MAP_VIEWPORT,

    /** Nearby. */
    NEARBY,

    /** Saved search. */
    SAVED_SEARCH,

    /** Free text. */
    FREE_TEXT
}

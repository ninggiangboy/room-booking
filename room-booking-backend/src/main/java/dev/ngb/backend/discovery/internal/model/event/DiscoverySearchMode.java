package dev.ngb.backend.discovery.internal.model.event;

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

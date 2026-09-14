package dev.ngb.backend.model;

/**
 * Where a saved search stands.
 */
public enum SavedSearchState {

    /** Running. */
    ACTIVE,

    /** Kept but not running. */
    PAUSED,

    /** Past the date the subscription stopped. */
    EXPIRED,

    /** Removed by the guest. */
    DELETED
}

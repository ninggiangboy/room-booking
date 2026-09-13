package dev.ngb.backend.model;

/**
 * Whether the listing is currently saved.
 *
 * <p>Unsaving keeps the row so that the history of saving and unsaving survives.</p>
 */
public enum SavedListingState {

    /** Currently saved by the guest. */
    SAVED,

    /** Saved once and since removed, which is weaker evidence than a stated problem. */
    UNSAVED
}

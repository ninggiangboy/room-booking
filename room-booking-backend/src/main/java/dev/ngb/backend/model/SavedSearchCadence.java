package dev.ngb.backend.model;

/**
 * How often a guest agreed to hear about matches to a saved search.
 *
 * <p>Anything but {@code NEVER} makes the saved search a standing subscription, which then has to
 * name the consent permitting it and the date it stops asking.</p>
 */
public enum SavedSearchCadence {

    /** A bookmark; nothing is ever sent. */
    NEVER,

    /** As soon as a match appears. */
    INSTANT,

    /** Once a day. */
    DAILY,

    /** Once a week. */
    WEEKLY
}

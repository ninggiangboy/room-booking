package dev.ngb.backend.model;

/**
 * Who or what withheld a stretch of nights.
 *
 * <p>Provenance decides the rules. An {@link #ICAL} import may replace the blocks a previous run of
 * that same import created, but it must never delete a {@link #HOST} block someone made by hand —
 * and without the source recorded there is no way to tell the two apart.</p>
 */
public enum BlockSource {
    /** The host withheld the nights themselves. */
    HOST,
    /** A platform operator withheld them. */
    OPERATIONS,
    /** Withheld for repairs or cleaning. */
    MAINTENANCE,
    /** Imported from an external calendar feed. */
    ICAL,
    /** Pushed by a channel manager integration. */
    CHANNEL_MANAGER
}

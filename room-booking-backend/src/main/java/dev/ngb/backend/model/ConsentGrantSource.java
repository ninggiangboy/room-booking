package dev.ngb.backend.model;

/**
 * Where a consent was collected.
 */
public enum ConsentGrantSource {
    /** During account creation. */
    SIGNUP,
    /** In the notification settings. */
    PREFERENCE_CENTRE,
    /** Recorded by an agent, with evidence. */
    SUPPORT,
    /** Migrated from an earlier system. */
    IMPORT,
    /** During a booking checkout. */
    CHECKOUT
}

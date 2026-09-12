package dev.ngb.backend.model;

/**
 * Who set a notification preference.
 */
public enum PreferenceSource {
    /** The recipient chose it. */
    USER,
    /** A market or purpose default applies. */
    POLICY_DEFAULT,
    /** Set on the recipient's behalf, with a reference to the case. */
    SUPPORT,
    /** Migrated from an earlier system. */
    IMPORT
}

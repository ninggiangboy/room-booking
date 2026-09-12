package dev.ngb.backend.model;

/**
 * Why a publication interval opened.
 */
public enum PublicationReason {
    /** The cycle revealed. */
    CYCLE_REVEAL,
    /** A removal was reversed. */
    MODERATION_RESTORED,
    /** A corrected version replaced what was shown. */
    LEGAL_CORRECTION,
    /** Imported history, recorded as an interval like everything else. */
    LEGACY_IMPORT
}

package dev.ngb.backend.model;

/**
 * When a submitted review may become visible.
 */
public enum ReviewRevealRule {
    /** Sealed until both sides submit or the deadline passes. */
    DOUBLE_BLIND,
    /** Visible as soon as moderation allows. */
    IMMEDIATE,
    /** What imported history did, recorded honestly rather than restated as double blind. */
    LEGACY_IMMEDIATE_PUBLICATION
}

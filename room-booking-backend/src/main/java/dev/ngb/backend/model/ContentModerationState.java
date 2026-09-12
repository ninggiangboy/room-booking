package dev.ngb.backend.model;

/**
 * Whether host-supplied content has been cleared for display.
 *
 * <p>{@link #FLAGGED} is distinct from {@link #REJECTED}: one is a suspicion needing review, the
 * other a decision already taken, and a host is owed the difference.</p>
 */
public enum ContentModerationState {
    /** Not yet reviewed. */
    PENDING,
    /** Cleared for display. */
    APPROVED,
    /** Refused; must not be displayed. */
    REJECTED,
    /** Suspected of a problem and awaiting review. */
    FLAGGED
}

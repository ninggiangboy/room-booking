package dev.ngb.backend.model;

/**
 * Health of a calendar connection.
 *
 * <p>{@link #FAILING} is separate from {@link #DISABLED} because a feed that has stopped responding
 * is still supposed to be working, and a host needs telling. A silently failing import is how the
 * same apartment ends up sold twice.</p>
 */
public enum CalendarConnectionStatus {
    /** Syncing normally. */
    ACTIVE,
    /** Deliberately paused by the host. */
    PAUSED,
    /** Repeatedly failing and needing attention. */
    FAILING,
    /** Switched off. */
    DISABLED
}

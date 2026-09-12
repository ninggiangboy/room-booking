package dev.ngb.backend.model;

/**
 * Which way calendar data flows over a connection.
 *
 * <p>The two need different secrets and carry different risks. An {@link #IMPORT} reads somebody
 * else's feed; an {@link #EXPORT} hands out a URL that discloses a host's whole occupancy pattern,
 * which is why its token is stored only as a digest.</p>
 */
public enum CalendarSyncDirection {
    /** Reading an external feed into this platform. */
    IMPORT,
    /** Publishing this platform's calendar outward. */
    EXPORT
}

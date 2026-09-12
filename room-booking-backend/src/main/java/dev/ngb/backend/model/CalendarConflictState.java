package dev.ngb.backend.model;

/**
 * Whether an external calendar is claiming nights this platform has already sold.
 *
 * <p>Conflicts are recorded rather than resolved automatically. Either answer — cancelling our
 * booking or ignoring the other channel's — strands a real guest somewhere, and that is a decision a
 * person has to make rather than a rule a sync job can apply.</p>
 */
public enum CalendarConflictState {
    /** No conflict. */
    NONE,
    /** A conflict has been found and needs a decision. */
    DETECTED,
    /** Someone decided and acted. */
    RESOLVED,
    /** Deliberately accepted without action. */
    IGNORED
}

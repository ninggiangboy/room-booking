package dev.ngb.backend.stay.internal.model.incident;

/**
 * What an incident event did to the response clock.
 */
public enum SloClockEffect {
    /** Nothing. */
    NONE,
    /** Started it. */
    START,
    /** Paused it, awaiting somebody. */
    PAUSE,
    /** Resumed it. */
    RESUME,
    /** Stopped it. */
    STOP
}

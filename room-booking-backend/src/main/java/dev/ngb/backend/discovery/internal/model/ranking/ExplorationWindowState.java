package dev.ngb.backend.discovery.internal.model.ranking;

/**
 * Where an exploration budget window stands.
 *
 * <p>{@code EXHAUSTED} is reached by spending the allocation, not by being labelled.</p>
 */
public enum ExplorationWindowState {

    /** Open. */
    OPEN,

    /** Exhausted. */
    EXHAUSTED,

    /** Suspended. */
    SUSPENDED,

    /** Closed. */
    CLOSED
}

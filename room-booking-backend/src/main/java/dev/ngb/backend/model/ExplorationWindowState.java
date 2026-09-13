package dev.ngb.backend.model;

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

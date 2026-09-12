package dev.ngb.backend.model;

/**
 * Whether a block is still withholding nights.
 *
 * <p>{@link #CONFLICT} records a block that could not be applied because the nights were already
 * sold. It is kept rather than discarded, because the fact that another channel believes it sold
 * those nights is exactly what an operator needs to see.</p>
 */
public enum BlockStatus {
    /** Currently withholding the nights. */
    ACTIVE,
    /** Lifted. */
    RELEASED,
    /** Replaced by a newer version of the same external event. */
    SUPERSEDED,
    /** Could not be applied because the nights were already sold. */
    CONFLICT
}

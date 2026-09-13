package dev.ngb.backend.model;

/**
 * Where an erasure directive stands.
 *
 * <p>{@code PARTIALLY_COMPLETED} is a real outcome and is recorded as one rather than rounded up.</p>
 */
public enum ErasureDirectiveState {

    /** Accepted and not yet started. */
    PENDING,

    /** Being propagated to derived stores. */
    IN_PROGRESS,

    /** Every store reported. */
    COMPLETED,

    /** Some store could not comply, recorded rather than rounded up. */
    PARTIALLY_COMPLETED,

    /** Propagation failed and must be retried. */
    FAILED
}

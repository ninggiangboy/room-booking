package dev.ngb.backend.model;

/**
 * What happened to an amount at a reserve.
 *
 * <p>Every subtraction from a host's available balance names an item. A reserve that is only a number
 * on a profile cannot be explained, appealed, or unwound.</p>
 */
public enum ReserveMovement {
    /** Moved into the reserve from a named payable allocation. */
    HELD,
    /** Returned to the host. */
    RELEASED,
    /** Used to fund a recovery. */
    CONSUMED
}

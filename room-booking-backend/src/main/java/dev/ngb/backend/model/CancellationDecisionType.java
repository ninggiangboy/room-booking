package dev.ngb.backend.model;

/**
 * What a committed cancellation decision did to the contract.
 *
 * <p>Wider than {@link CancellationActionType} by one value: a correction fixes a decision that was
 * itself wrong, and no guest was ever shown a preview of it.</p>
 */
public enum CancellationDecisionType {
    /** The whole stay ended. At most one live per booking. */
    FULL_CANCELLATION,
    /** Part of the stay ended. */
    PARTIAL_CANCELLATION,
    /** The terms changed. */
    MODIFICATION,
    /** The guest never arrived. Also terminal for the booking. */
    NO_SHOW,
    /** The guest moved to different supply. */
    RELOCATION,
    /** An earlier decision was superseded by a corrected one. */
    CORRECTION
}

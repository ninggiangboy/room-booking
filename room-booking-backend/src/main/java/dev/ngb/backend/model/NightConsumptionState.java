package dev.ngb.backend.model;

/**
 * What became of the inventory behind one night of a revision.
 *
 * <p>Shortening a stay does not delete its nights; it leaves them readable with the night marked
 * released, so that a later question about what the guest paid for still has an answer.</p>
 */
public enum NightConsumptionState {
    /** The night is claimed and the guest holds it. */
    CONSUMED,
    /** The claim was given back to the calendar. */
    RELEASED,
    /** The claim moved to another revision or another resource. */
    TRANSFERRED,
    /** No inventory was ever held for this night. */
    NOT_CLAIMED
}

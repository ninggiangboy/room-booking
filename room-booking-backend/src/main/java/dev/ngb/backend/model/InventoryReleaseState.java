package dev.ngb.backend.model;

/**
 * Progress of handing nights back to the calendar.
 *
 * <p>The release is an instruction rather than a direct write, because inventory is another domain's
 * authority and the write may fail, be retried, or arrive after a sweeper already released the claim.</p>
 */
public enum InventoryReleaseState {
    /** Waiting to be applied. */
    PENDING,
    /** The claim was released, under a recorded fencing token. */
    APPLIED,
    /** The attempt failed and is worth retrying. */
    FAILED,
    /** Given up on; needs an operator. */
    ABANDONED,
    /** No claim was held, so there is nothing to give back. */
    NOT_REQUIRED
}

package dev.ngb.backend.model;

/**
 * What inventory said about an emergency calendar block.
 *
 * <p>Operations asks; inventory owns the block and its overlap consequences.</p>
 */
public enum BlockRequestState {
    /** No block has been asked for. */
    NOT_REQUESTED,
    /** Asked for and awaiting an answer. */
    REQUESTED,
    /** Inventory created the block, which this record names. */
    APPLIED,
    /** Inventory refused it. */
    REJECTED
}

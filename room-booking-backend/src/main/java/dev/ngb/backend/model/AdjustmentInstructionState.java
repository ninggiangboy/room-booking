package dev.ngb.backend.model;

/**
 * Progress of handing an adjustment to the domain that owns it.
 */
public enum AdjustmentInstructionState {
    /** Written and waiting to be dispatched. */
    PENDING,
    /** Sent to the target domain. */
    DISPATCHED,
    /** The target domain confirmed it took the instruction. */
    ACKNOWLEDGED,
    /** Dispatch failed and is worth retrying. */
    FAILED,
    /** Given up on; needs an operator. */
    ABANDONED
}

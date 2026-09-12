package dev.ngb.backend.model;

/**
 * Who currently holds a piece of linked evidence.
 */
public enum EvidenceCustodyState {
    /** Held by operations for this incident. */
    HELD,
    /** Handed to another domain, which is named. */
    TRANSFERRED,
    /** No longer needed for this incident. */
    RELEASED,
    /** Deleted under retention. Impossible while a legal hold stands. */
    PURGED
}

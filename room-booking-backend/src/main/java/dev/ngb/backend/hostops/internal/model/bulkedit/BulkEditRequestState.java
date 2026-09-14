package dev.ngb.backend.hostops.internal.model.bulkedit;

/**
 * Where a bulk edit stands. APPLIED means every target applied; anything less is PARTIALLY_APPLIED,
 * and the difference is the point of recording targets at all.
 */
public enum BulkEditRequestState {

    /** Being composed; nothing has been shown or written. */
    DRAFT,

    /** The host has seen what it would do, and the digest of that preview is on the row. */
    PREVIEWED,

    /** Being applied; this is the only state in which target outcomes may be written. */
    APPLYING,

    /** Every target applied. */
    APPLIED,

    /** Some targets were skipped or refused, and each says which and why. */
    PARTIALLY_APPLIED,

    /** The edit could not be applied at all. */
    FAILED,

    /** Abandoned before it began applying. */
    CANCELLED
}

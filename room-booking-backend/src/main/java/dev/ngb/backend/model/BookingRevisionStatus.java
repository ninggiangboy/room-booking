package dev.ngb.backend.model;

/**
 * Lifecycle of one booking revision.
 *
 * <p>At most one revision per booking is {@code COMMITTED} at a time, enforced by a partial unique
 * index. A committed revision is frozen by trigger: superseding it is a new row, editing it is not
 * possible.</p>
 */
public enum BookingRevisionStatus {
    /** Being assembled. Its nights may still be written. */
    DRAFT,
    /** The current terms. Frozen. */
    COMMITTED,
    /** Replaced by a later revision. Still the authority for what was agreed then. */
    SUPERSEDED,
    /** A draft that was never committed. */
    ABANDONED
}

package dev.ngb.backend.model;

/**
 * Whether one claimed resource within a booking still stands.
 *
 * <p>Per-item rather than per-booking so that cancelling one of three hotel rooms releases exactly
 * one room's nights and one room's money, instead of forcing the whole stay to be re-created.</p>
 */
public enum BookingItemStatus {
    /** The item holds its nights. */
    ACTIVE,
    /** The item was cancelled; its nights are released and its history is kept. */
    CANCELLED,
    /** The item was changed by an applied modification. */
    MODIFIED,
    /** A later revision replaced this item. */
    SUPERSEDED
}

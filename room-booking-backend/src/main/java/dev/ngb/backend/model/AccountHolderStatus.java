package dev.ngb.backend.model;

/**
 * Whether an account holder may own supply, contract, and be settled.
 *
 * <p>Holders are never deleted. A closed holder still has to explain the bookings it contracted and
 * the money it was paid, so the row stays and its status changes.</p>
 */
public enum AccountHolderStatus {
    /** Fully usable. */
    ACTIVE,
    /** Temporarily barred from new activity; existing obligations stand. */
    SUSPENDED,
    /** Permanently closed; retained for history and settlement. */
    CLOSED
}

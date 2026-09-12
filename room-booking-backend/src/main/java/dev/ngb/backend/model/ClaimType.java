package dev.ngb.backend.model;

/**
 * What is consuming a stretch of inventory.
 *
 * <p>All four kinds live in one table deliberately. If holds, bookings, host blocks, and imported
 * reservations each had their own storage, nothing would stop a host blocking a night a guest is
 * simultaneously holding — the database can only refuse a conflict it can see in one place.</p>
 */
public enum ClaimType {
    /** A temporary reservation while a guest completes checkout. */
    HOLD,
    /** A confirmed stay. */
    BOOKING,
    /** Nights withheld by a host or operator. */
    BLOCK,
    /** A stay sold through another channel and imported here. */
    EXTERNAL_RESERVATION
}

package dev.ngb.backend.model;

/**
 * The capacity in which a party appears on a booking.
 *
 * <p>The booking holder and the person actually staying are not always the same, and a co-host may
 * act for the host without being the host. Naming the capacity keeps the distinction usable by
 * messaging, access, and support rather than leaving it implied.</p>
 */
public enum BookingPartyRole {
    /** The account that holds the booking. */
    GUEST,
    /** The account that owns the supply. */
    HOST,
    /** A delegate acting for the host. */
    CO_HOST,
    /** The person actually staying, when not the booking holder. */
    PRIMARY_GUEST
}

package dev.ngb.backend.model;

/**
 * Progress of one attempt to turn an offer into a booking.
 *
 * <p>This is the guest-facing journey: the sequence a checkout screen follows and a countdown is
 * drawn from. It lives here, on the attempt, rather than on the booking, because most attempts never
 * produce a booking at all and the booking's own state is not a single line.</p>
 */
public enum CheckoutStatus {
    /** Started, with terms not yet accepted. */
    DRAFT,
    /** Inventory is held for this attempt. */
    HELD,
    /** Waiting on a payment outcome. */
    PAYMENT_PENDING,
    /** Waiting on a host decision. */
    HOST_PENDING,
    /** A booking exists; the attempt is over and succeeded. */
    SUCCEEDED,
    /** The deadline passed with no outcome. */
    EXPIRED,
    /** The attempt ended without a booking, for a recorded reason. */
    FAILED
}

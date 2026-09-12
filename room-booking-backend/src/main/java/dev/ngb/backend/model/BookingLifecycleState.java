package dev.ngb.backend.model;

/**
 * Whether the booking is a contract, and whether it still is one.
 *
 * <p>This is one of five independent dimensions on a booking. It answers only whether the agreement
 * exists and stands; it says nothing about whether money moved or whether the guest arrived, which
 * are {@link BookingPaymentState} and {@link StayState}. Collapsing those into this enum is what
 * produces states like {@code CONFIRMED_REFUND_FAILED} that multiply with every new combination.</p>
 */
public enum BookingLifecycleState {
    /**
     * Holding inventory, not yet a contract.
     *
     * <p>A booking in this state always carries a deadline, enforced by
     * {@code ck_bookings_provisional_deadline}, because nights held with no expiry are nights no
     * sweeper can ever reclaim.</p>
     */
    PROVISIONAL,
    /** A binding agreement for a future stay. */
    CONFIRMED,
    /** Ended before or during the stay; inventory release and refund follow separately. */
    CANCELLED,
    /** The stay happened and the contract is discharged. */
    COMPLETED,
    /** Confirmed, but the guest never arrived; a decision backed by evidence, not by silence. */
    NO_SHOW
}

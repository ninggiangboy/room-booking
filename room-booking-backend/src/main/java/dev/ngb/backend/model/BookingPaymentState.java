package dev.ngb.backend.model;

/**
 * How far the money for a booking has progressed.
 *
 * <p>A projection the payment domain writes onto the booking, never a value a caller may set to
 * whatever it likes. Payments submit verified provider outcomes to the booking orchestrator; they do
 * not reach in and assign statuses. The authoritative history lives in the payment tables that
 * migration 021 introduces.</p>
 */
public enum BookingPaymentState {
    /** No payment has been attempted. */
    NOT_STARTED,
    /** The provider needs the guest to complete a challenge before it can decide. */
    REQUIRES_ACTION,
    /** Funds are reserved but not taken. */
    AUTHORIZED,
    /** Funds have been taken. */
    CAPTURED,
    /** The attempt failed and no funds are held. */
    FAILED,
    /** An authorization was released without capture. */
    VOIDED,
    /** Captured funds have been returned. */
    REFUNDED
}

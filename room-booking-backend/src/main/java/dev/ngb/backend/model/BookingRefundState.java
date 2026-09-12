package dev.ngb.backend.model;

/**
 * How far money owed back to the guest has travelled.
 *
 * <p>Deliberately independent of {@link BookingLifecycleState}. A booking can be cancelled and its
 * nights resold while a refund is still pending or has outright failed, and a failed refund must
 * never reactivate a stay that has already been given away.</p>
 */
public enum BookingRefundState {
    /** Nothing is owed back. */
    NOT_REQUIRED,
    /** A refund is owed and has not completed. */
    PENDING,
    /** Part of what is owed has been returned. */
    PARTIAL,
    /** Everything owed has been returned. */
    REFUNDED,
    /** The attempt failed; the debt to the guest stands and needs intervention. */
    FAILED
}

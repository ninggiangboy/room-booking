package dev.ngb.backend.model;

/**
 * Which of a booking's independent state dimensions a transition moved.
 *
 * <p>Named explicitly on every transition so that a payment reaching {@code CAPTURED} and a stay
 * reaching {@code CHECKED_IN} read as different kinds of event. Without it the history is a column
 * of unrelated words whose meaning depends on knowing which machine produced each row.</p>
 */
public enum BookingStateDimension {
    /** Whether the contract exists and stands. */
    LIFECYCLE,
    /** How far the money has progressed. */
    PAYMENT,
    /** What happened at the property. */
    STAY,
    /** Whether a replacement is in flight. */
    CHANGE,
    /** How far money owed back has travelled. */
    REFUND
}

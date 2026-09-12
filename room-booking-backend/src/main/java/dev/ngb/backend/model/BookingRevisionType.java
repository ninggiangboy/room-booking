package dev.ngb.backend.model;

/**
 * Why a booking revision exists.
 *
 * <p>The type is not decoration: a correction and a modification produce the same shape of row but
 * different obligations, and a compensating revision exists only to undo one that should not have
 * been committed.</p>
 */
public enum BookingRevisionType {
    /** The terms the booking was created with. Always revision one. */
    ORIGINAL,
    /** A change both parties agreed to. */
    MODIFICATION,
    /** A fix to terms that were recorded wrongly, with no change in agreement. */
    CORRECTION,
    /** A revision that reverses an earlier one, leaving both readable. */
    COMPENSATING
}

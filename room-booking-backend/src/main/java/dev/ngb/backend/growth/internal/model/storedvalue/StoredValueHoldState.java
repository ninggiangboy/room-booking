package dev.ngb.backend.growth.internal.model.storedvalue;

/**
 * Where credit held against an open quote stands.
 */
public enum StoredValueHoldState {

    /** Set aside against an open quote. */
    HELD,

    /** Spent on the booking the quote became. */
    CONSUMED,

    /** Given back, for a recorded reason. */
    RELEASED,

    /** Given back because the hold lapsed. */
    EXPIRED
}

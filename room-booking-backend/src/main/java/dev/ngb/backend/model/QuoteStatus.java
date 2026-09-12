package dev.ngb.backend.model;

/**
 * Whether an offer still stands.
 *
 * <p>A quote is never re-priced in place. Anything that would change the answer produces a new quote
 * and marks this one {@code SUPERSEDED}, because an offer that can change between being shown and
 * being accepted is not an offer the guest agreed to.</p>
 */
public enum QuoteStatus {
    /** Live and acceptable until its expiry. */
    OPEN,
    /** Taken up; its amounts become the booking's financial basis. */
    ACCEPTED,
    /** Lapsed without being accepted. */
    EXPIRED,
    /** Replaced by a newer quote for the same trip. */
    SUPERSEDED,
    /** Withdrawn because it should never have been offered. */
    VOID
}

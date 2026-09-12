package dev.ngb.backend.model;

/**
 * What a modification proposal would change.
 *
 * <p>Named rather than inferred from the delta rows, because the allowed change types differ by rate
 * plan and by market, and the check happens before any delta is calculated.</p>
 */
public enum ModificationChangeType {
    /** Different check-in or check-out. */
    DATES,
    /** A different party size. */
    GUEST_COUNT,
    /** More or fewer units of the same supply. */
    UNIT_QUANTITY,
    /** A move to different supply under the same booking. */
    LISTING_TRANSFER,
    /** A different rate plan, and therefore different terms. */
    RATE_PLAN,
    /** A price fix with no change to what is being sold. */
    PRICE_CORRECTION,
    /** Several of the above in one proposal. */
    MIXED
}

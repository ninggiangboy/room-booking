package dev.ngb.backend.model;

/**
 * The source kind of {@code case_remedy_lines}.
 */
public enum RemedySourceKind {

    /** Booking line. */
    BOOKING_LINE,

    /** Quote line. */
    QUOTE_LINE,

    /** Tax line. */
    TAX_LINE,

    /** Platform fee. */
    PLATFORM_FEE,

    /** Host payout allocation. */
    HOST_PAYOUT_ALLOCATION,

    /** Claim item. */
    CLAIM_ITEM,

    /** Protection limit. */
    PROTECTION_LIMIT,

    /** Goodwill budget. */
    GOODWILL_BUDGET,

    /** Deposit. */
    DEPOSIT,

    /** Not applicable. */
    NOT_APPLICABLE
}

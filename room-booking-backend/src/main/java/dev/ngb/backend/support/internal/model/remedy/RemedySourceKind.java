package dev.ngb.backend.support.internal.model.remedy;

import dev.ngb.backend.pricing.internal.model.quote.Quote;
import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.pricing.internal.model.quote.Quote;

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

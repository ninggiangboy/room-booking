package dev.ngb.backend.model;

/**
 * What a line on a host statement represents.
 *
 * <p>Every line traces to a posting, a payout item, or an explicit allocation. Totals are composed
 * from these lines rather than recomputed from today's fee, tax, or cancellation settings, which is
 * what makes a two-year-old statement still reproducible.</p>
 */
public enum StatementLineType {
    /** What the host earned before any deduction. */
    GROSS_ENTITLEMENT,
    /** A discount the host chose to fund. */
    HOST_FUNDED_DISCOUNT,
    /** The platform's share of the booking. */
    COMMISSION,
    /** A platform fee charged to the host. */
    PLATFORM_FEE,
    /** Tax on a platform fee or commission. */
    FEE_TAX,
    /** Tax withheld under an approved authority decision. */
    WITHHOLDING,
    /** Moved into a reserve. */
    RESERVE_HELD,
    /** Returned from a reserve. */
    RESERVE_RELEASED,
    /** Money returned to the guest that reduces the host's share. */
    REFUND,
    /** The effect of a cancellation on this host's earnings. */
    CANCELLATION,
    /** An amount affected by an open dispute. */
    DISPUTE,
    /** An amount reclaimed through a chargeback. */
    CHARGEBACK,
    /** An amount collected against a debt the host owes. */
    RECOVERY,
    /** An approved manual correction. */
    ADJUSTMENT,
    /** The transfer itself. */
    PAYOUT,
    /** A fee charged for making the transfer. */
    PAYOUT_FEE,
    /** A transfer that came back. */
    PAYOUT_RETURN
}

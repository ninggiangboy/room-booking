package dev.ngb.backend.model;

/**
 * What a quote line is for.
 *
 * <p>The type drives settlement, not presentation: it decides whether the amount is commissionable,
 * whether it is refundable by default, and which party ends up with it. A cleaning fee filed as
 * accommodation reaches the wrong ledger account even though the guest paid the same total.</p>
 */
public enum QuoteLineType {
    /** The nightly rate for the stay. */
    ACCOMMODATION,
    /** A one-off cleaning charge. */
    CLEANING_FEE,
    /** The platform's fee to the guest. */
    SERVICE_FEE,
    /** The platform's fee to the host. */
    HOST_FEE,
    /** A charge for guests beyond the standard occupancy. */
    EXTRA_GUEST_FEE,
    /** A charge for bringing an animal. */
    PET_FEE,
    /** A mandatory property or resort charge. */
    RESORT_FEE,
    /** A reduction granted by a price rule. */
    DISCOUNT,
    /** A reduction granted by a promotion. */
    PROMOTION,
    /** Tax, which is never itself taxable and never commissionable. */
    TAX,
    /** A refundable amount held against damage rather than earned. */
    SECURITY_DEPOSIT,
    /** Anything else, described by its component code. */
    OTHER
}

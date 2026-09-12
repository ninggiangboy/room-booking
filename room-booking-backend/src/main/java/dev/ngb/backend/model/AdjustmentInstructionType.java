package dev.ngb.backend.model;

/**
 * What a cancellation decision set in motion that is not a refund.
 *
 * <p>These share one table because they share one problem: each must be produced exactly once from a
 * decision and handed to a domain that will act on it. They differ only in which domain reads them.</p>
 */
public enum AdjustmentInstructionType {
    /** Credit issued to the guest instead of money returned. */
    GUEST_CREDIT,
    /** An amount to be collected back from the host. */
    HOST_RECOVERY,
    /** An amount the platform funds itself. */
    PLATFORM_GOODWILL,
    /** A redeemed promotion returned to its holder. */
    PROMOTION_RESTORE,
    /** A correction the tax domain must post. */
    TAX_ADJUSTMENT,
    /** A document to reissue, such as a credit note. */
    DOCUMENT_REQUEST,
    /** A hold to place on the host's payouts. */
    PAYOUT_HOLD,
    /** A hold to lift. */
    PAYOUT_RELEASE
}

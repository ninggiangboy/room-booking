package dev.ngb.backend.model;

/**
 * Where commission owed on one credited booking stands.
 *
 * <p>A commission that has been paid is recovered through migration 022’s recovery path rather
 * than reversed here: money that has already left is not un-sent by editing a row.</p>
 */
public enum AffiliateCommissionState {

    /** Owed but still inside the payout hold. */
    PENDING,

    /** Posted to the ledger and waiting for payment. */
    EARNED,

    /** Sent through a payout instruction. */
    PAID,

    /** Undone before payment, with a reversing posting. */
    REVERSED,

    /** Held back, for a recorded reason. */
    WITHHELD
}

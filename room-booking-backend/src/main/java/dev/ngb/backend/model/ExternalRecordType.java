package dev.ngb.backend.model;

/**
 * What one row of external financial evidence describes.
 */
public enum ExternalRecordType {
    /** Money taken from a guest. */
    CHARGE,
    /** Money returned to a guest. */
    REFUND,
    /** A provider fee. */
    FEE,
    /** A provider-initiated correction. */
    ADJUSTMENT,
    /** A movement between provider balances. */
    TRANSFER,
    /** A transfer out to a bank account. */
    PAYOUT,
    /** A transfer that was sent back. */
    PAYOUT_RETURN,
    /** Money reclaimed by a cardholder's bank. */
    CHARGEBACK,
    /** A chargeback that was itself reversed. */
    CHARGEBACK_REVERSAL,
    /** A row the parser recognised but does not classify. */
    OTHER
}

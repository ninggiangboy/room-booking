package dev.ngb.backend.growth.internal.model.storedvalue;

/**
 * What kind of stored value a balance holds.
 */
public enum StoredValueAccountKind {

    /** Credit from a growth programme. */
    PROMOTIONAL_CREDIT,

    /** Value somebody paid for. */
    GIFT_CARD_BALANCE,

    /** Credit given by support. */
    GOODWILL_CREDIT,

    /** A refund the guest chose to take as credit. */
    REFUND_CREDIT
}

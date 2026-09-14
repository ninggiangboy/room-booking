package dev.ngb.backend.growth.internal.model.storedvalue;

/**
 * Where the value in one credit lot came from.
 */
public enum StoredValueSourceKind {

    /** Earned by referring or being referred. */
    REFERRAL_REWARD,

    /** Granted by a campaign. */
    CAMPAIGN_GRANT,

    /** Redeemed from a card. */
    GIFT_CARD,

    /** Given by support. */
    GOODWILL,

    /** A refund taken as credit. */
    REFUND_TO_CREDIT,

    /** Part of a loyalty tier. */
    LOYALTY_BENEFIT,

    /** Granted by hand, on the record. */
    MANUAL_AWARD
}

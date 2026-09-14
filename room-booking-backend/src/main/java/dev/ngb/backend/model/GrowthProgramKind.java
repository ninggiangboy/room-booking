package dev.ngb.backend.model;

/**
 * Which acquisition or retention mechanism a growth programme is.
 */
public enum GrowthProgramKind {

    /** Existing guests invite new ones and both sides are rewarded. */
    REFERRAL,

    /** Credit is granted directly, for goodwill or as part of a launch. */
    CREDIT_GRANT,

    /** Repeat activity earns a tier and the benefits attached to it. */
    LOYALTY,

    /** People are contacted with an offer or a reminder. */
    CAMPAIGN,

    /** Stored value is sold and redeemed later. */
    GIFT_CARD,

    /** A partner is paid for bookings that followed its link. */
    AFFILIATE
}

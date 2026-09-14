package dev.ngb.backend.model;

/**
 * Whether a referral reward arrives as stored value or as a discount.
 */
public enum ReferralRewardKind {

    /** Stored value in the beneficiary’s balance. */
    CREDIT,

    /** A discount applied through migration 019. */
    PROMOTION
}

package dev.ngb.backend.growth.internal.model.referral;

/**
 * Which half of a two-sided referral reward a grant pays.
 */
public enum ReferralBeneficiarySide {

    /** The person who did the referring. */
    REFERRER,

    /** The person who was referred. */
    REFEREE
}

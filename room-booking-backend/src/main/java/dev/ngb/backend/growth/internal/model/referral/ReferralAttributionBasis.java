package dev.ngb.backend.growth.internal.model.referral;

/**
 * How a referral between two people was established.
 */
public enum ReferralAttributionBasis {

    /** The referee followed an invitation addressed to them. */
    INVITATION_LINK,

    /** The referee typed the code in. */
    CODE_ENTERED,

    /** The two were matched on a contact the referrer had shared. */
    CONTACT_MATCH
}

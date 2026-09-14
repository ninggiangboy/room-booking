package dev.ngb.backend.growth.internal.model.referral;

/**
 * How a referral invitation was delivered.
 *
 * <p>The two addressed channels store a digest of the address and never the address itself; a
 * shared link has no addressee at all.</p>
 */
public enum ReferralInvitationChannel {

    /** Sent to an email address. */
    EMAIL,

    /** Sent to a phone number. */
    SMS,

    /** A link the referrer shared themselves, with no addressee. */
    LINK,

    /** Posted on a social platform. */
    SOCIAL
}

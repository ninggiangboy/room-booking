package dev.ngb.backend.trust.internal.model.subject;

/**
 * What a risk record is about.
 *
 * <p>One stable typed handle for anything the platform can assess, so every table in the trust and
 * safety domain spells the same actor the same way. An {@code ACCOUNT} subject resolves to a real
 * account holder; the rest name identifiers owned by other domains.</p>
 */
public enum RiskSubjectType {
    /** One account holder. */
    ACCOUNT,
    /** A host organization rather than a person. */
    ORGANIZATION,
    /** A public listing. */
    LISTING,
    /** A property behind one or more listings. */
    PROPERTY,
    /** One booking. */
    BOOKING,
    /** A content item under moderation. */
    CONTENT,
    /** A tokenized payment instrument; never the credential itself. */
    PAYMENT_INSTRUMENT,
    /** A payout destination claim. */
    PAYOUT_DESTINATION,
    /** A device reference, salted and short lived. */
    DEVICE,
    /** A network or address range observation. */
    NETWORK,
    /** A promotion or referral programme. */
    PROMOTION,
    /** One conversation between parties. */
    CONVERSATION,
    /** A review record. */
    REVIEW
}

package dev.ngb.backend.model;

/**
 * What two subjects appear to have in common.
 *
 * <p>Hotels, families, offices and carriers create benign connections, so a link is a question rather
 * than a finding. Using one to justify an adverse decision requires corroboration, which is a check
 * constraint on the link itself.</p>
 */
public enum EntityLinkRelation {
    /** Seen on the same device reference. */
    SHARED_DEVICE,
    /** Seen on the same network. */
    SHARED_NETWORK,
    /** The same tokenized payment instrument. */
    SHARED_INSTRUMENT,
    /** The same payout destination. */
    SHARED_PAYOUT,
    /** The same address. */
    SHARED_ADDRESS,
    /** The same contact channel. */
    SHARED_CONTACT,
    /** One referred the other. */
    REFERRAL,
    /** A booking between them. */
    BOOKED_WITH,
    /** One reviewed the other. */
    REVIEWED,
    /** They exchanged messages. */
    MESSAGED,
    /** Membership of the same organization. */
    ORGANIZATION_MEMBER,
    /** They may be the same party. */
    SUSPECTED_DUPLICATE;
}

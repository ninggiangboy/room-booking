package dev.ngb.backend.model;

/**
 * What entitles a participant to be in a conversation.
 *
 * <p>Recorded so that access can be re-derived later: a co-host who loses listing authority loses
 * the thread, and an agent whose case assignment ended cannot read on yesterday's grounds.</p>
 */
public enum ParticipantAuthoritySource {
    /** Named on the booking itself. */
    BOOKING_PARTY,
    /** Holds operational authority over the listing. */
    LISTING_OPERATOR,
    /** Delegated through a property collaborator record. */
    PROPERTY_COLLABORATOR,
    /** Assigned to a support case, with a purpose code and an expiry. */
    SUPPORT_ASSIGNMENT,
    /** Emergency moderator access, with a reason and enhanced logging. */
    MODERATION_ACCESS,
    /** The platform acting as itself. */
    PLATFORM
}

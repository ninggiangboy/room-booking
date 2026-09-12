package dev.ngb.backend.model;

/**
 * Why a participant is in a conversation.
 *
 * <p>The role is not the permission set. Permissions are enumerated per participant, because
 * "the host" is a changing group of people and a thread must remember who was entitled when.</p>
 */
public enum ConversationRole {
    /** The contracting guest or an approved travel-party member. */
    GUEST,
    /** The booking host. */
    HOST,
    /** A delegated operator of the listing. */
    CO_HOST,
    /** A support agent with time-bound, purpose-bound access. */
    SUPPORT,
    /** The platform itself, which writes system facts and has no account holder. */
    SYSTEM
}

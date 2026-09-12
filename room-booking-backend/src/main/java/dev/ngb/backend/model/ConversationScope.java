package dev.ngb.backend.model;

/**
 * What a conversation is about.
 *
 * <p>Threads exist for a scope rather than for a pair of people, so that membership can follow
 * current authority over that scope. An inquiry that becomes a booking gets its own booking
 * thread rather than silently widening the pre-booking history to newly added operators.</p>
 */
public enum ConversationScope {
    /** A guest and the listing operator before any booking exists, with restricted disclosure. */
    INQUIRY,
    /** The participants in one accepted booking. */
    BOOKING,
    /** A restricted thread about one incident. */
    INCIDENT,
    /** Controlled case communication with purpose-bound agent access. */
    SUPPORT
}

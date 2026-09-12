package dev.ngb.backend.model;

/**
 * Whether a conversation still accepts messages.
 */
public enum ConversationStatus {
    /** Ordinary participation. */
    OPEN,
    /** Sending is limited by policy; the reason is recorded on the row. */
    RESTRICTED,
    /** No further messages; history remains readable. */
    CLOSED,
    /** Withdrawn from ordinary presentation, which frees the scope for a new thread. */
    ARCHIVED
}

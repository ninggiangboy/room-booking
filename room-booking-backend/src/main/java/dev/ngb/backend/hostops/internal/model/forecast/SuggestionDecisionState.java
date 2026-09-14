package dev.ngb.backend.hostops.internal.model.forecast;

/**
 * What a host did about a promotion suggestion.
 */
public enum SuggestionDecisionState {

    /** Open; the estimate may still be revised because nobody has acted on it. */
    PENDING,

    /** The host ran it, and the promotion it became is named on the row. */
    ACCEPTED,

    /** The host declined it. */
    REJECTED,

    /** It went stale before the host reached it. */
    EXPIRED,

    /** The platform pulled it back before the host acted. */
    WITHDRAWN
}

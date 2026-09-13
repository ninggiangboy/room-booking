package dev.ngb.backend.model;

/**
 * Who the content was written for.
 *
 * <p>Scanning a private message is bounded by an approved purpose and notice, so the audience is
 * part of the record rather than an assumption made at review time.</p>
 */
public enum ContentAudienceScope {
    /** Anyone. */
    PUBLIC,
    /** Signed-in users. */
    AUTHENTICATED,
    /** The parties to one conversation or booking. */
    PARTICIPANTS,
    /** The author alone. */
    PRIVATE,
    /** Platform staff. */
    INTERNAL;
}

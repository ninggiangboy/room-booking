package dev.ngb.backend.trust.internal.model.content;

/**
 * How the person reporting relates to what they reported.
 *
 * <p>Relationship informs weight and conflict rules. It is not proof: a competitor and a guest may
 * both be right or both be wrong.</p>
 */
public enum ReporterRelationship {
    /** A party to the conversation or booking. */
    PARTICIPANT,
    /** Somebody who saw it publicly. */
    AUDIENCE,
    /** The host of the listing concerned. */
    HOST,
    /** The guest of the booking concerned. */
    GUEST,
    /** Somebody otherwise uninvolved. */
    THIRD_PARTY,
    /** A public authority. */
    AUTHORITY,
    /** Platform staff or automation. */
    PLATFORM;
}

package dev.ngb.backend.model;

/**
 * What a mention said about its aspect.
 */
public enum AspectSentiment {
    /** Favourable. */
    POSITIVE,
    /** Unfavourable. */
    NEGATIVE,
    /** Both, in one breath. */
    MIXED,
    /** Mentioned without judgement. */
    NEUTRAL,
    /** Could not be determined. */
    UNKNOWN
}

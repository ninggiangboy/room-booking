package dev.ngb.backend.model;

/**
 * Where in a review an aspect mention came from.
 */
public enum AspectMentionSource {
    /** The written text, with a span. */
    FREE_TEXT,
    /** A structured category score. */
    CATEGORY_RATING,
    /** A structured question the reviewer answered. */
    STRUCTURED_ANSWER,
    /** A caption on an attached file. */
    MEDIA_CAPTION
}

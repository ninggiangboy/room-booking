package dev.ngb.backend.review.internal.model;

/**
 * Which way a measure has moved recently.
 */
public enum TrendDirection {
    /** Recent evidence is better than the long-run value. */
    IMPROVING,
    /** No material change. */
    STABLE,
    /** Recent evidence is worse than the long-run value. */
    DECLINING,
    /** Not enough recent evidence to say. */
    UNKNOWN
}

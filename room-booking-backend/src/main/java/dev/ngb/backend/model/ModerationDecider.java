package dev.ngb.backend.model;

/**
 * Whose authority an intervention rests on.
 */
public enum ModerationDecider {
    /** A deterministic rule with a version. */
    POLICY,
    /** A classifier proposal. */
    MODEL,
    /** A named person. */
    REVIEWER,
    /** An automatic control such as a rate limit. */
    SYSTEM
}

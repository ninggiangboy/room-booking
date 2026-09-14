package dev.ngb.backend.discovery.internal.model.profile;

/**
 * Which way a preference points, kept separate from how much it matters.
 *
 * <p>{@code UNKNOWN} states that the direction is not established; it may not also state a preferred
 * level.</p>
 */
public enum PreferenceDirection {

    /** The guest prefers more of it. */
    POSITIVE,

    /** The guest prefers less of it. */
    NEGATIVE,

    /** The guest prefers a band rather than an extreme. */
    TARGET_RANGE,

    /** The direction is not established, and no preferred level may be stated. */
    UNKNOWN
}

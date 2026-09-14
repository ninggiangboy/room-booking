package dev.ngb.backend.review.internal.model.record_;

/**
 * Who private feedback was written for.
 */
public enum PrivateFeedbackAudience {
    /** The host alone. */
    HOST,
    /** The guest alone. */
    GUEST,
    /** The platform, not either party. */
    PLATFORM,
    /** Support handling a case. */
    SUPPORT
}

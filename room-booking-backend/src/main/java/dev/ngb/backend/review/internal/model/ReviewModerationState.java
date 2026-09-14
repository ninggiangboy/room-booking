package dev.ngb.backend.review.internal.model;

/**
 * What content moderation currently says about a review.
 *
 * <p>Owned by trust and safety; projected here because visibility depends on it.</p>
 */
public enum ReviewModerationState {
    /** Not yet judged. */
    PENDING,
    /** Allowed as written. */
    PUBLISH,
    /** Allowed with part of it hidden. */
    MASK,
    /** Held pending human review. */
    QUARANTINE,
    /** Refused before ever being shown. */
    REJECT,
    /** Taken down after being shown. */
    REMOVE,
    /** Put back after an appeal or correction. */
    RESTORED
}

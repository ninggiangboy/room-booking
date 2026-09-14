package dev.ngb.backend.review.internal.model.record_;
import dev.ngb.backend.review.internal.model.ReviewModerationState;

/**
 * The moderation outcome applied to an exact revision.
 *
 * <p>Deliberately narrower than {@link ReviewModerationState}: there is no such thing as applying a
 * "pending" decision, and a Java value wider than its check constraint would fail at runtime.</p>
 */
public enum ModerationApplicationAction {
    /** Allowed as written. */
    PUBLISH,
    /** Allowed with part hidden. */
    MASK,
    /** Held pending human review. */
    QUARANTINE,
    /** Refused before publication. */
    REJECT,
    /** Taken down. */
    REMOVE,
    /** Put back. */
    RESTORED
}

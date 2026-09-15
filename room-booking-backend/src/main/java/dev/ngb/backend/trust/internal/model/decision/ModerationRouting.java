package dev.ngb.backend.trust.internal.model.decision;

/**
 * Where a detector thinks the item should go next.
 *
 * <p>A recommendation about routing, never about the outcome. The outcome vocabulary lives on the
 * decision and nowhere else, because model confidence does not map to severity across languages and
 * content types.</p>
 */
public enum ModerationRouting {
    /** Nothing further is suggested. */
    NO_ACTION,
    /** A person should look at it. */
    HUMAN_REVIEW,
    /** Route to the urgent safety path. */
    URGENT_SAFETY,
    /** Hold until a policy question is settled. */
    HOLD_FOR_POLICY
}

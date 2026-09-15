package dev.ngb.backend.trust.internal.model.decision;

/**
 * What the owning domain is asked to do about visibility.
 *
 * <p>{@code UNCHANGED} is what a safety escalation carries: routing a threat somewhere is not the
 * same as deciding whether the words stay up, and conflating them answers a report by hiding it.</p>
 */
public enum ModerationVisibilityInstruction {
    /** Show it. */
    VISIBLE,
    /** Show it with the masked spans replaced. */
    VISIBLE_MASKED,
    /** Show it behind a warning. */
    VISIBLE_WITH_WARNING,
    /** Withdraw it from view. */
    HIDDEN,
    /** Never make it visible. */
    NOT_PUBLISHED,
    /** Leave visibility exactly as it is. */
    UNCHANGED
}

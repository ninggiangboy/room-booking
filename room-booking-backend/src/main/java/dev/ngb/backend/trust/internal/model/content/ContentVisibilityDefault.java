package dev.ngb.backend.trust.internal.model.content;

/**
 * How a revision is treated before a decision is taken.
 *
 * <p>A revision carrying an unscanned attachment may not default to visible, which is a check
 * constraint rather than a step in the upload path.</p>
 */
public enum ContentVisibilityDefault {
    /** Shown to its audience immediately. */
    VISIBLE,
    /** Withheld until a decision. */
    PENDING,
    /** Withheld because validation or scanning has not passed. */
    QUARANTINED,
    /** Not shown. */
    HIDDEN;
}

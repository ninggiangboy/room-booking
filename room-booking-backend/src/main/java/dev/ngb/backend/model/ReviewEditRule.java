package dev.ngb.backend.model;

/**
 * How long an author may replace their own submission.
 *
 * <p>The default exists to prevent retaliation: editing stops the moment the counterpart speaks.</p>
 */
public enum ReviewEditRule {
    /** Until the other side submits or the window closes, whichever is first. */
    UNTIL_COUNTERPART_OR_DEADLINE,
    /** Until the window closes, regardless of the counterpart. */
    UNTIL_DEADLINE,
    /** Not at all. */
    NO_EDIT
}

package dev.ngb.backend.model;

/**
 * What a reviewer did.
 *
 * <p>A reviewer never edits an automated decision. Overturning one requires producing a new
 * decision, which is a check constraint on the action row.</p>
 */
public enum ReviewActionType {
    /** The existing decision stands. */
    CONFIRM_DECISION,
    /** A new decision replaces it. */
    OVERTURN_DECISION,
    /** More evidence was asked for. */
    REQUEST_EVIDENCE,
    /** A restriction was issued. */
    ISSUE_RESTRICTION,
    /** A restriction was lifted. */
    LIFT_RESTRICTION,
    /** A challenge was issued. */
    ISSUE_CHALLENGE,
    /** The task moved to another queue. */
    ESCALATE,
    /** The task was closed without a decision. */
    CANCEL,
    /** A note was added and nothing changed. */
    ANNOTATE;
}

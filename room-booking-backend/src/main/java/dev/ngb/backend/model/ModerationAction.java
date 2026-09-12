package dev.ngb.backend.model;

/**
 * What policy decided to do about a message.
 *
 * <p>A model score is a proposal. The two outcomes that restrict a person or escalate a safety
 * case may not be attributed to a classifier alone, and the database refuses a row that tries.</p>
 */
public enum ModerationAction {
    /** No intervention. */
    ALLOW,
    /** The author is warned. */
    WARN,
    /** Ordinary recipients see a policy-approved projection. */
    MASK,
    /** The message is withheld pending review. */
    QUARANTINE,
    /** Delivery is held back. */
    DELAY,
    /** Queued for a reviewer. */
    HUMAN_REVIEW,
    /** The author loses a capability. */
    RESTRICT,
    /** Routed to the safety queue. */
    SAFETY_ESCALATE
}

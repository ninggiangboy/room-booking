package dev.ngb.backend.model;

/**
 * Why a member’s loyalty tier changed.
 *
 * <p>The two discretionary reasons require a named approver, because a tier reached outside the
 * published thresholds is a decision somebody made rather than a threshold somebody met.</p>
 */
public enum LoyaltyTransitionReason {

    /** The member joined, so there is no tier before. */
    ENROLLED,

    /** They met the thresholds for a higher tier. */
    QUALIFIED,

    /** They fell below the thresholds they held. */
    DOWNGRADED,

    /** Somebody granted the tier outside the thresholds. */
    MANUAL_GRANT,

    /** A status held with a partner was matched across. */
    PARTNER_STATUS_MATCH,

    /** The programme ended and everybody came back down. */
    PROGRAM_CLOSED
}

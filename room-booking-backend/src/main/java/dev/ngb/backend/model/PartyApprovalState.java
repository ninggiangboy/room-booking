package dev.ngb.backend.model;

/**
 * Whether one side of a proposed change has agreed.
 *
 * <p>{@code NOT_REQUIRED} is a real answer, not an absence: some changes need only one party, and
 * recording that explicitly is what lets the commit rule be checked by the row itself.</p>
 */
public enum PartyApprovalState {
    /** This party's agreement is not needed for this change. */
    NOT_REQUIRED,
    /** Asked and not yet answered. */
    PENDING,
    /** Agreed, with the instant recorded. */
    APPROVED,
    /** Refused. */
    DECLINED
}

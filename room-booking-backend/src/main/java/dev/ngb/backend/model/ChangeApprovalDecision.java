package dev.ngb.backend.model;

/**
 * Whether a reviewer agreed to a change or refused it.
 */
public enum ChangeApprovalDecision {

    /** Agreed to the value as proposed. */
    APPROVED,

    /** Refused, with a comment saying why. */
    REJECTED
}

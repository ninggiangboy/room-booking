package dev.ngb.backend.model;

/**
 * Whether an operator assignment still confers authority.
 */
public enum RoleAssignmentState {

    /** In force now. */
    ACTIVE,

    /** Ran to its end date. */
    EXPIRED,

    /** Withdrawn before its end date, with a reason recorded. */
    REVOKED
}

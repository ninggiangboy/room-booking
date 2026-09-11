package dev.ngb.backend.model;

/**
 * Outcome recorded on a configuration approval.
 *
 * <p>Rejections are recorded as deliberately as approvals: an approval trail that showed only
 * successes could not demonstrate that a proposal was considered and refused.</p>
 */
public enum ApprovalDecision {
    /** The subject may be activated. */
    APPROVED,
    /** The subject was refused and must not be activated. */
    REJECTED,
    /** A prior approval was withdrawn. */
    REVOKED
}

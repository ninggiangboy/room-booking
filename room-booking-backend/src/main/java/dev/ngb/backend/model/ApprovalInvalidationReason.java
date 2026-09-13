package dev.ngb.backend.model;

/**
 * The invalidation reason of {@code case_decision_approvals}.
 */
public enum ApprovalInvalidationReason {

    /** Digest changed. */
    DIGEST_CHANGED,

    /** Authority revoked. */
    AUTHORITY_REVOKED,

    /** Expired. */
    EXPIRED,

    /** Superseded. */
    SUPERSEDED
}

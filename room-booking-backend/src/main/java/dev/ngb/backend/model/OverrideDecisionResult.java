package dev.ngb.backend.model;

/**
 * The judgement on one booking's eligibility for an override.
 *
 * <p>Rejections are recorded as fully as approvals, because the guest will ask why, and because a
 * pattern of rejections is how a badly scoped programme is discovered.</p>
 */
public enum OverrideDecisionResult {
    /** The override applies to this booking. */
    APPROVED,
    /** It does not. */
    REJECTED,
    /** Waiting on evidence or a wider decision. */
    DEFERRED,
    /** The applicant took the request back. */
    WITHDRAWN
}

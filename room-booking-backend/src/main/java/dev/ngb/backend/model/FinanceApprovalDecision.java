package dev.ngb.backend.model;

/**
 * What one approver decided about one adjustment request.
 *
 * <p>Recorded append-only and unique per approver, so one person cannot satisfy a two-approval
 * threshold by clicking twice.</p>
 */
public enum FinanceApprovalDecision {
    /** This approver assents to the exact request they saw. */
    APPROVED,
    /** This approver refuses it, with a reason. */
    REJECTED
}

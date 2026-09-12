package dev.ngb.backend.model;

/**
 * Progress of one relocation expense claim.
 *
 * <p>Approval and reimbursement are separate states because they happen at different times and by
 * different authorities: an agent approves the amount, and the payment domain moves it.</p>
 */
public enum RelocationExpenseState {
    /** Submitted with a receipt reference. */
    CLAIMED,
    /** An approver accepted it, for no more than was claimed. */
    APPROVED,
    /** Refused, with a reason. */
    REJECTED,
    /** A refund instruction exists and the money is on its way. */
    REIMBURSED
}

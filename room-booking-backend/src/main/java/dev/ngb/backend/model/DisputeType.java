package dev.ngb.backend.model;

/**
 * Kind of external challenge to a captured payment.
 *
 * <p>None of these is a refund. A refund is a decision the platform makes; a dispute is a process
 * somebody else starts, with somebody else's deadlines.</p>
 */
public enum DisputeType {
    /** A question that may precede a formal case. */
    INQUIRY,
    /** A request for information or evidence. */
    RETRIEVAL,
    /** A formal reversal claim against a capture. */
    CHARGEBACK,
    /** A second round after a contested chargeback outcome. */
    PRE_ARBITRATION,
    /** Escalation to the scheme's decision. */
    ARBITRATION
}

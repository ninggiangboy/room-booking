package dev.ngb.backend.model;

/**
 * Lifecycle of a proposed change to a live contract.
 *
 * <p>At most one proposal per booking is live, enforced by a partial unique index. Two open proposals
 * would each hold different nights and each believe they describe the next revision.</p>
 */
public enum ModificationProposalStatus {
    /** Waiting for a party to respond. */
    OPEN,
    /** Every required party agreed; not yet committed. */
    ACCEPTED,
    /** A new revision was produced from it. */
    COMMITTED,
    /** A party refused. The reason is recorded. */
    DECLINED,
    /** Nobody responded before the deadline. */
    EXPIRED,
    /** The initiator took it back. */
    WITHDRAWN
}

package dev.ngb.backend.model;

/**
 * Lifecycle of the entitlement to have money returned.
 *
 * <p>Distinct from {@link RefundExecutionState}, which the payment domain owns. This state says whether
 * the entitlement stands; that one says whether the money moved. An instruction may only be withdrawn
 * while no execution has accepted it, because after that the money may already be in flight.</p>
 */
public enum RefundInstructionState {
    /** Decided and waiting for the payment domain to pick it up. */
    ISSUED,
    /** An execution exists against it. */
    ACCEPTED,
    /** The payment domain reported the whole amount returned. */
    SETTLED,
    /** Withdrawn before any execution accepted it. */
    CANCELLED,
    /** Replaced by a reissued version of the same instruction. */
    SUPERSEDED
}

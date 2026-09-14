package dev.ngb.backend.admin.internal.model.change;

/**
 * Where a change request stands between being raised and taking effect.
 * <p>It cannot reach {@code APPROVED} without the evidence and the sign-offs its impact requires,
 * and it cannot be applied without being approved.</p>
 */
public enum ChangeRequestState {

    /** Being written; the only state in which the proposal can be edited. */
    DRAFT,

    /** Every check its schema requires has run and passed. */
    VALIDATED,

    /** Open for the sign-offs its impact class requires. */
    AWAITING_APPROVAL,

    /** Every required role has agreed to the value now proposed. */
    APPROVED,

    /** In effect. */
    APPLIED,

    /** Refused by a reviewer who said why. */
    REJECTED,

    /** Taken back by the person who raised it. */
    WITHDRAWN,

    /** Overtaken by a later request about the same thing. */
    SUPERSEDED
}

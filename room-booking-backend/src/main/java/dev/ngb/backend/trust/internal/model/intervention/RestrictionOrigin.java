package dev.ngb.backend.trust.internal.model.intervention;

/**
 * Where a restriction came from.
 *
 * <p>A restriction inherited from the old account status column carries neither policy nor decision,
 * and a check constraint stops one being invented for it: the row says plainly that it came from a
 * system which did not record why.</p>
 */
public enum RestrictionOrigin {
    /** Produced by a policy evaluation. */
    RISK_DECISION,
    /** Issued by an authorized reviewer. */
    REVIEWER_ACTION,
    /** Issued or amended by an appeal. */
    APPEAL_OUTCOME,
    /** Required by a legal instruction. */
    LEGAL_ORDER,
    /** Backfilled from a historical account status, with no policy behind it. */
    LEGACY_STATUS
}

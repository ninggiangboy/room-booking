package dev.ngb.backend.model;

/**
 * One step of the recovery waterfall.
 *
 * <p>The order is recorded as a step number so a statement can show that the reserve was used before
 * future earnings were touched. That ordering is the difference between a contractual offset and an
 * unexplained deduction.</p>
 */
public enum HostRecoveryMethod {
    /** Taken from an unapplied host reserve. */
    RESERVE_APPLIED,
    /** Taken from money the host could have been paid now. */
    AVAILABLE_PAYABLE_OFFSET,
    /** Taken from earnings the host has not made yet. */
    FUTURE_PAYABLE_OFFSET,
    /** Collected from the host directly, under an authorised mandate. */
    AUTHORIZED_DEBIT,
    /** Funded by a protection, insurance, or partner arrangement. */
    PROTECTION_RECOVERY,
    /** Given up as a platform loss, under approval. */
    WRITTEN_OFF
}

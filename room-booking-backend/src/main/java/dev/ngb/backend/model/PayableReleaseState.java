package dev.ngb.backend.model;

/**
 * Whether an allocation may enter a payout.
 *
 * <p>Availability is independent of ownership. A held or reserved amount is still the host's; it is
 * simply not payable yet.</p>
 */
public enum PayableReleaseState {
    /** No release policy has been evaluated against it. */
    NOT_SCHEDULED,
    /** A release instant is set and has not arrived. */
    SCHEDULED,
    /** Matured and eligible for a payout. */
    AVAILABLE,
    /** Stopped by an explicit hold. */
    HELD,
    /** Claimed by a live payout instruction. */
    RESERVED,
    /** Paid out. */
    CONSUMED
}

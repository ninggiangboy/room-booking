package dev.ngb.backend.model;

/**
 * How far ownership of a nominated payout destination has been proven.
 *
 * <p>Only a {@link #VERIFIED} destination may receive money. This is the control that makes payout
 * diversion hard: an attacker who takes over an account still has to prove ownership of the account
 * they want the money sent to.</p>
 */
public enum PayoutOwnershipState {
    /** Nominated, with no ownership evidence yet. */
    UNVERIFIED,
    /** Ownership evidence submitted and being checked. */
    PENDING,
    /** Ownership proven; the destination may receive money. */
    VERIFIED,
    /** Ownership evidence refused. */
    REJECTED
}

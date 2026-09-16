package dev.ngb.backend.identity.internal.model.account;

/**
 * Whether an account holder may own supply, contract, and be settled.
 *
 * <p>Holders are never deleted. A closed holder still has to explain the bookings it contracted and
 * the money it was paid, so the row stays and its status changes.</p>
 */
public enum AccountHolderStatus {
    /**
     * Created, primary contact channel unproven. Authentication is permitted — {@link
     * dev.ngb.backend.identity.internal.service.account.AccountHolderFinder}'s "active" checks
     * treat this the same as {@link #ACTIVE} — but {@link AccountHolder#canTransact()} still
     * requires {@link #ACTIVE}, and which capabilities beyond that a holder in this state should
     * lose remains an open product decision; see
     * {@code docs/implementation/identity/09-roadmap.md#pending_verification-state}.
     */
    PENDING_VERIFICATION,
    /** Fully usable. */
    ACTIVE,
    /** Temporarily barred from new activity; existing obligations stand. */
    SUSPENDED,
    /**
     * The holder asked to close their own account. Authentication is denied — like {@link
     * #SUSPENDED}, {@code AccountHolderFinder}'s "active" checks exclude this state — and every
     * session and unconsumed token was revoked in the same transaction as the transition. Nothing
     * in this codebase yet checks the obligations (future stays, unsettled balances, open cases)
     * D01 says should block completion; an operator moves a holder out of this state by hand
     * through {@code AdminAccountService.completeDeletion}. See
     * {@code docs/implementation/identity/09-roadmap.md#erasure}.
     */
    DELETION_REQUESTED,
    /** Permanently closed; retained for history and settlement. */
    CLOSED
}

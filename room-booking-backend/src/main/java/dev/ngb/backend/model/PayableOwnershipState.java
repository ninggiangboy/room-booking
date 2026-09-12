package dev.ngb.backend.model;

/**
 * Whose money an allocation is.
 *
 * <p>Kept apart from {@link PayableReleaseState} deliberately. Owning an amount and being able to
 * receive it are different questions, and collapsing them into one status is how an amount silently
 * stops being the host's the moment a hold is placed on it.</p>
 */
public enum PayableOwnershipState {
    /** Attributable to the host but not yet recognised as a liability. */
    PROVISIONAL,
    /** A ledger liability owed to the host. */
    PAYABLE,
    /** Negated by a later posting, such as a cancellation or refund. */
    REVERSED,
    /** Consumed by a recovery the host owes back. */
    RECOVERY_DUE
}

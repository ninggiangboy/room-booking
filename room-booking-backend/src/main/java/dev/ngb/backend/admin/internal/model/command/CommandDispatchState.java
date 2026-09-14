package dev.ngb.backend.admin.internal.model.command;

/**
 * Where an operational command stands between being asked for and being answered.
 */
public enum CommandDispatchState {

    /** Waiting for the sign-offs the command requires. */
    PENDING_APPROVAL,

    /** Has every approval it needs and has not gone yet. */
    APPROVED,

    /** Handed to the domain that owns the target. */
    DISPATCHED,

    /** The domain answered and did what was asked. */
    SUCCEEDED,

    /** The domain declined, under its own rules. */
    REFUSED,

    /** The command could not be completed at all. */
    FAILED,

    /** Withdrawn before it was sent. */
    CANCELLED
}

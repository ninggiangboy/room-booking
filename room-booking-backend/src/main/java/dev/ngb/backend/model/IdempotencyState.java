package dev.ngb.backend.model;

/**
 * Settlement state of a retry-safe command record.
 *
 * <p>A record is created {@code IN_PROGRESS} before the command runs and is settled exactly once.
 * The distinction matters to a replaying caller: a settled record can be answered from storage,
 * while an {@code IN_PROGRESS} record whose lease has lapsed must be recovered rather than assumed
 * to have had no effect.</p>
 */
public enum IdempotencyState {
    /** The command has been claimed but has not yet produced an outcome. */
    IN_PROGRESS,
    /** The command completed and its safe response projection can be replayed. */
    SUCCEEDED,
    /** The command failed with a classified failure code. */
    FAILED
}

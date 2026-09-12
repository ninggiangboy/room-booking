package dev.ngb.backend.model;

/**
 * Whether an account may be posted to.
 *
 * <p>An account that has been posted to is never deleted. Retirement stops future normal use from an
 * effective instant while leaving historical replay intact.</p>
 */
public enum LedgerAccountLifecycle {
    /** Defined but not yet approved for posting. */
    DRAFT,
    /** Approved and available to the posting engine. */
    ACTIVE,
    /** Closed to new postings; existing entries remain readable and replayable. */
    RETIRED
}

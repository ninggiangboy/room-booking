package dev.ngb.backend.model;

/**
 * What the deterministic reducer did with one observation.
 *
 * <p>Recorded so the reduction is reproducible. Evidence is never deleted for being stale or
 * unrecognised: it is kept and labelled, because a disagreement about money is settled from the
 * complete evidence set and not from the surviving part of it.</p>
 */
public enum ReducerOutcome {
    /** Advanced the operation's state. */
    APPLIED,
    /** Valid evidence that arrived after a higher-precedence fact. */
    IGNORED_STALE,
    /** Conflicts with internal facts on amount, currency, account, or ownership. */
    QUARANTINED,
    /** Could not be resolved to an internal operation. */
    UNMAPPED
}

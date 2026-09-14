package dev.ngb.backend.discovery.internal.model.personalization;

/**
 * What one derived store managed to do about an erasure directive.
 *
 * <p>{@code NOT_TECHNICALLY_FEASIBLE} is an acceptable answer only as a written one.</p>
 */
public enum ErasureApplicationOutcome {

    /** The store removed or rewrote what the directive reached. */
    APPLIED,

    /** The store held nothing within the directive. */
    NOTHING_TO_ERASE,

    /** Scheduled for a later run, with the reason recorded. */
    DEFERRED,

    /** The store cannot comply, which is acceptable only as a written answer. */
    NOT_TECHNICALLY_FEASIBLE,

    /** The attempt failed and must be retried. */
    FAILED
}

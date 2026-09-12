package dev.ngb.backend.model;

/**
 * Lifecycle of a cancellation decision.
 *
 * <p>A committed decision is frozen by trigger: its arithmetic, its authority and its policy citation
 * cannot change. Correcting it means a superseding decision that names it, because the original is the
 * evidence that the platform said what it said.</p>
 */
public enum CancellationDecisionStatus {
    /** Being assembled. Its lines may still be written. */
    DRAFT,
    /** Final. The deferred allocation rule has checked it. */
    COMMITTED,
    /** A corrected decision replaced it. */
    SUPERSEDED,
    /** Abandoned before commitment. */
    VOIDED
}

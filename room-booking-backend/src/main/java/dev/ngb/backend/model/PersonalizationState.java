package dev.ngb.backend.model;

/**
 * Whether a search was personalized, and if not, why not.
 *
 * <p>An anonymous guest, an opt-out, an unavailable profile, a confidence gate, an experiment control
 * arm, and an explicit sort mode are six different answers, and only one of them is a fault.</p>
 */
public enum PersonalizationState {

    /** Ordered for this guest in particular. */
    PERSONALIZED,

    /** No guest was identified, so the baseline served. */
    ANONYMOUS,

    /** The guest declined personalized ranking. */
    OPTED_OUT,

    /** A profile exists or could exist but could not be read in time. */
    PROFILE_UNAVAILABLE,

    /** The profile was too weak to be allowed to reorder strong results. */
    CONFIDENCE_GATED,

    /** Held in a control arm for comparison. */
    EXPERIMENT_CONTROL,

    /** The guest asked for a specific ordering, which bypasses recommendation. */
    EXPLICIT_SORT_MODE,

    /** Enrichment missed its deadline and was abandoned so the search stayed fast. */
    ENRICHMENT_DEADLINE_EXCEEDED
}

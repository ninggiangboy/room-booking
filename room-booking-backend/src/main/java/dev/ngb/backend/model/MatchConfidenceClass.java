package dev.ngb.backend.model;

/**
 * How much a match may be trusted to act on its own.
 *
 * <p>Fuzzy and model-assisted candidates may be recorded but may never post, close, or write off
 * money. The class is stored so the difference between "the provider object id matched exactly" and
 * "the amounts were close enough" stays visible afterwards.</p>
 */
public enum MatchConfidenceClass {
    /** An exact key match under an approved rule; may auto-close. */
    DETERMINISTIC,
    /** A composite match within stated tolerances; normally reviewed. */
    BOUNDED,
    /** A candidate for a person; never closes a comparison. */
    SUGGESTED
}

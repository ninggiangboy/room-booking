package dev.ngb.backend.model;

/**
 * Where a tax result came from.
 *
 * <p>A figure attributed to a provider must also name which provider and which of its responses, so
 * the result can be produced again when it is questioned. A manual figure is a human decision and is
 * treated as one.</p>
 */
public enum TaxCalculationSource {
    /** Computed from the platform's own published rule versions. */
    INTERNAL,
    /** Returned by an external tax engine. */
    PROVIDER,
    /** Entered by a person, with a reason. */
    MANUAL
}

package dev.ngb.backend.model;

/**
 * How much the platform actually knows about a party's tax position.
 *
 * <p>{@code DECLARED} is what the party told us and {@code VERIFIED} is what evidence supports.
 * Treating them as the same is how an unverified claim ends up deciding a withholding rate, and the
 * difference only surfaces when an authority disagrees years later.</p>
 */
public enum TaxProfileStatus {
    /** Self-reported, unchecked. */
    DECLARED,
    /** Supported by evidence from an accepted source. */
    VERIFIED,
    /** Contested, by the party or by an authority. */
    DISPUTED,
    /** Replaced by a later profile. */
    SUPERSEDED
}

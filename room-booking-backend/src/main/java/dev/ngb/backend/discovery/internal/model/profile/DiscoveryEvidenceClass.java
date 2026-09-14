package dev.ngb.backend.discovery.internal.model.profile;

/**
 * What a body of evidence supports being said.
 *
 * <p>{@code INSUFFICIENT} is the honest answer for a feature with nothing behind it, and it is a
 * different statement from {@code NEUTRAL}.</p>
 */
public enum DiscoveryEvidenceClass {

    /** Not enough evidence to say anything, which is not the same as nothing being wrong. */
    INSUFFICIENT,

    /** Consistently better than the comparison set. */
    STRENGTH,

    /** Consistently worse, a claim held to a stricter bar because it harms a host. */
    WEAKNESS,

    /** Evidence points both ways. */
    MIXED,

    /** Enough evidence to say it is unremarkable. */
    NEUTRAL
}

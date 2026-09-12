package dev.ngb.backend.model;

/**
 * What the evidence for one aspect amounts to.
 *
 * <p>{@code INSUFFICIENT} is the default and is not the same as {@code NEUTRAL}: missing evidence is
 * unknown, not bad. A check constraint refuses any other class with no mentions behind it.</p>
 */
public enum AspectEvidenceClass {
    /** Too little to say anything. */
    INSUFFICIENT,
    /** Consistently favourable. */
    STRENGTH,
    /** Consistently unfavourable. */
    WEAKNESS,
    /** Genuinely divided. */
    MIXED,
    /** Mentioned often without favour either way. */
    NEUTRAL
}

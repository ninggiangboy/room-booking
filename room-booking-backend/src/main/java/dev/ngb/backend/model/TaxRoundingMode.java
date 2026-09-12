package dev.ngb.backend.model;

/**
 * How a tax figure is rounded to whole minor units.
 *
 * <p>Named distinctly from {@link java.math.RoundingMode} because this is the authority's declared
 * policy, not a Java detail, and only the modes a jurisdiction actually mandates belong here.</p>
 */
public enum TaxRoundingMode {
    /** Halves go away from zero. */
    HALF_UP,
    /** Halves go to the nearest even value, reducing cumulative bias. */
    HALF_EVEN,
    /** Always towards zero. */
    DOWN,
    /** Always away from zero. */
    UP
}

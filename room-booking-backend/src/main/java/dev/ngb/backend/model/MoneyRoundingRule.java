package dev.ngb.backend.model;

/**
 * How a split amount's remainder is disposed of.
 *
 * <p>Deliberately separate from {@link TaxRoundingMode}, which serves tax arithmetic and answers to a
 * tax authority. This one governs allocating one amount across several lines, where the question is
 * not "what is correct" but "who gets the odd unit" -- and the answer must be reproducible.</p>
 */
public enum MoneyRoundingRule {
    /** Ties move away from zero. */
    HALF_UP,
    /** Ties move to the even neighbour, which does not drift over many splits. */
    HALF_EVEN,
    /** Always down. */
    FLOOR,
    /** Always up. */
    CEILING,
    /** Remainders go to the lines with the largest fractional parts, so the parts still sum to the whole. */
    LARGEST_REMAINDER
}

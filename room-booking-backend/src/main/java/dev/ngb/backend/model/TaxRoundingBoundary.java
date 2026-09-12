package dev.ngb.backend.model;

/**
 * At which point in the calculation rounding happens.
 *
 * <p>It is not cosmetic. Rounding per night and rounding once on the total give different answers for
 * the same stay, and the difference is a real amount owed to a real authority. The calculation is
 * carried at full precision and rounded exactly once, here.</p>
 */
public enum TaxRoundingBoundary {
    /** After each unit of quantity. */
    UNIT,
    /** After each night. */
    NIGHT,
    /** After each line. */
    LINE,
    /** After each tax within a line. */
    TAX,
    /** Once per issued document. */
    INVOICE,
    /** Once on the final total. */
    TOTAL
}

package dev.ngb.backend.model;

/**
 * Whether a money line adds to what is owed or takes away from it.
 *
 * <p>Amounts are stored unsigned and paired with this, because a negative number is ambiguous — it
 * can mean a discount, a refund, a correction, or a sign error — and ambiguity in money is a defect
 * rather than a style question.</p>
 */
public enum LineDirection {
    /** Increases the amount owed. */
    CHARGE,
    /** Decreases it. */
    CREDIT
}

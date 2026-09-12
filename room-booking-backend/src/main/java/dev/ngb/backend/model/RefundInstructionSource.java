package dev.ngb.backend.model;

/**
 * Which kind of decision entitled somebody to a refund.
 *
 * <p>Everything except a correction traces to a committed decision row. A correction is the one case
 * where a support decision stands alone, and it is recorded as such rather than disguised.</p>
 */
public enum RefundInstructionSource {
    /** A cancellation decision. */
    CANCELLATION,
    /** A modification that reduced what was owed. */
    MODIFICATION,
    /** A remedy agreed in a support case. */
    REMEDY,
    /** A correction of an earlier error, with no originating decision. */
    CORRECTION,
    /** A relocation case outcome. */
    RELOCATION
}

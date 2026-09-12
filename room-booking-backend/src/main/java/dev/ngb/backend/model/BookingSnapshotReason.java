package dev.ngb.backend.model;

/**
 * Why a financial snapshot of a booking was taken.
 *
 * <p>Stored because a sequence of amounts with no reasons cannot be audited. The reason is what lets
 * finance tell a renegotiated stay apart from a goodwill adjustment when both produce the same
 * delta.</p>
 */
public enum BookingSnapshotReason {
    /** The contract was formed. */
    CONFIRMATION,
    /** A modification changed what is owed. */
    MODIFICATION,
    /** A cancellation changed what is owed. */
    CANCELLATION,
    /** Money changed without the stay changing, such as service recovery. */
    ADJUSTMENT,
    /** The stay finished and final amounts were fixed. */
    COMPLETION
}

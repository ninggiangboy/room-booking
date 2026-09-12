package dev.ngb.backend.model;

/**
 * Which committed fact a scheduled communication hangs from.
 *
 * <p>The anchor and its version are what a worker revalidates before sending, so a modified or
 * cancelled booking cannot leave a stale reminder able to fire.</p>
 */
public enum ScheduleAnchorDomain {
    /** A booking or one of its revisions. */
    BOOKING,
    /** A collection obligation or schedule item. */
    PAYMENT,
    /** A cancellation decision. */
    CANCELLATION,
    /** A review cycle. */
    REVIEW,
    /** An incident follow-up. */
    INCIDENT,
    /** A payout instruction. */
    PAYOUT
}

package dev.ngb.backend.model;

/**
 * Whether an imported external reservation still stands.
 *
 * <p>Cancelled rows are retained rather than deleted, so that nights reappearing on the calendar can
 * be explained by something other than data loss.</p>
 */
public enum ExternalReservationStatus {
    /** The external channel still reports this stay. */
    ACTIVE,
    /** The external channel has withdrawn it. */
    CANCELLED,
    /** Replaced by a newer version of the same event. */
    SUPERSEDED
}

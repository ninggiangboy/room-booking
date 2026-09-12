package dev.ngb.backend.model;

/**
 * How far a reserve has run.
 */
public enum HostReserveState {
    /** Holding, and may still take more up to its cap. */
    ACTIVE,
    /** Reached its maturity instant and is releasing. */
    MATURED,
    /** Returned to the host in full. */
    RELEASED,
    /** Finished, whether by release or by consumption. */
    CLOSED
}

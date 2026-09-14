package dev.ngb.backend.stay.internal.model.stay;

/**
 * Whether anything is currently wrong with this stay.
 */
public enum StayIncidentExposure {
    /** No incident is open. */
    NONE,
    /** An ordinary service incident is open. */
    OPEN_NON_SAFETY,
    /** A safety incident is open; automation is restricted. */
    OPEN_SAFETY,
    /** Every incident reached an operational resolution. */
    RESOLVED,
    /** Ownership moved to support, trust or claims. */
    TRANSFERRED
}

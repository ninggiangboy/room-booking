package dev.ngb.backend.model;

/**
 * Where a price or availability alert stands.
 */
public enum DemandAlertState {

    /** Watching. */
    ACTIVE,

    /** Has fired. */
    TRIGGERED,

    /** Stopped watching at its own expiry. */
    EXPIRED,

    /** Turned off by the guest. */
    CANCELLED
}

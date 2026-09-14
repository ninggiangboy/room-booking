package dev.ngb.backend.stay.internal.model.access;

/**
 * What is being asked of an access provider.
 */
public enum AccessOperationType {
    /** Create a credential. */
    PROVISION,
    /** Replace an existing credential. */
    ROTATE,
    /** Withdraw a credential. */
    REVOKE,
    /** Ask the provider for current state. */
    QUERY,
    /** Widen an existing credential's validity. */
    EXTEND
}

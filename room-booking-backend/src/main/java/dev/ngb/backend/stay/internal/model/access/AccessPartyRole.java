package dev.ngb.backend.stay.internal.model.access;

/**
 * Whose entitlement an access grant carries.
 */
public enum AccessPartyRole {
    /** The booking guest. */
    GUEST,
    /** The host. */
    HOST,
    /** A co-host. */
    CO_HOST,
    /** A listing operator acting for the host. */
    OPERATOR,
    /** A support agent under a time-bound assignment. */
    SUPPORT,
    /** A cleaner, inspector or other engaged worker. */
    CONTRACTOR
}

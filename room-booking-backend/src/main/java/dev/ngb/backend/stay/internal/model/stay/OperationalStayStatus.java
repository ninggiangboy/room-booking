package dev.ngb.backend.stay.internal.model.stay;

/**
 * Whether this operational view is the one in force.
 */
public enum OperationalStayStatus {
    /** The current operational view of its booking. */
    ACTIVE,
    /** A newer booking revision produced a replacement stay. */
    SUPERSEDED,
    /** Operations are finished with it. */
    CLOSED
}

package dev.ngb.backend.model;

/**
 * Where a host stands under the platform hosting standards. Anything other than good standing is a
 * consequence, and it carries its explanation on the same row.
 */
public enum HostComplianceState {

    /** Nothing is held against the host, and no explanation is owed. */
    IN_GOOD_STANDING,

    /** Something is trending badly but nothing has been applied. */
    WATCH,

    /** The host has been told, and what they were told is on the row. */
    WARNED,

    /** A consequence has been applied. */
    RESTRICTED,

    /** A person is looking at it before anything else happens. */
    UNDER_REVIEW
}

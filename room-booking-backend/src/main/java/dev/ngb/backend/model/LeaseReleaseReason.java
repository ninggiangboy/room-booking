package dev.ngb.backend.model;

/**
 * The release reason of {@code work_item_leases}.
 */
public enum LeaseReleaseReason {

    /** Completed. */
    COMPLETED,

    /** Abandoned. */
    ABANDONED,

    /** Expired. */
    EXPIRED,

    /** Transferred. */
    TRANSFERRED,

    /** Revoked. */
    REVOKED
}

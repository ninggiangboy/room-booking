package dev.ngb.backend.support.internal.model.remedy;

/**
 * The release reason of {@code remedy_reservations}.
 */
public enum ReservationReleaseReason {

    /** Remedy rejected. */
    REMEDY_REJECTED,

    /** Remedy expired. */
    REMEDY_EXPIRED,

    /** Lease expired. */
    LEASE_EXPIRED,

    /** Superseded. */
    SUPERSEDED,

    /** Manual release. */
    MANUAL_RELEASE
}

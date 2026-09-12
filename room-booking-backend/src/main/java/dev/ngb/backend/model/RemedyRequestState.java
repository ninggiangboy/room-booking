package dev.ngb.backend.model;

/**
 * Where one cross-domain ask stands.
 */
public enum RemedyRequestState {
    /** Recorded, not yet sent. */
    REQUESTED,
    /** Sent to the owning domain. */
    DISPATCHED,
    /** Acknowledged and being decided. */
    PENDING,
    /** Accepted, naming that domain's own decision. */
    ACCEPTED,
    /** Refused, with a reason. */
    REJECTED,
    /** Carried out, naming the resulting decision. */
    COMPLETED,
    /** Could not be delivered or processed. */
    FAILED,
    /** Operations withdrew the ask. */
    WITHDRAWN
}

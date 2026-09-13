package dev.ngb.backend.model;

/**
 * The state of {@code remedy_reservations}.
 */
public enum RemedyReservationState {

    /** Held. */
    HELD,

    /** Consumed. */
    CONSUMED,

    /** Released. */
    RELEASED,

    /** Expired. */
    EXPIRED
}

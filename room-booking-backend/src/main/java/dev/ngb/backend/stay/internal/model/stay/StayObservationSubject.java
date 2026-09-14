package dev.ngb.backend.stay.internal.model.stay;

/**
 * What an observation is about.
 */
public enum StayObservationSubject {
    /** The state of the property. */
    PROPERTY_READINESS,
    /** A guest arriving. */
    GUEST_ARRIVAL,
    /** A guest leaving. */
    GUEST_DEPARTURE,
    /** Somebody being at the property. */
    PRESENCE,
    /** How many people are staying. */
    OCCUPANCY,
    /** Use of metered services. */
    CONSUMPTION,
    /** The condition of the property or its contents. */
    CONDITION
}

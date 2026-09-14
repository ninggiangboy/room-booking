package dev.ngb.backend.stay.internal.model.task;

/**
 * What kind of defect a maintenance record is about.
 */
public enum MaintenanceCategory {
    /** Water, drainage or sanitary fittings. */
    PLUMBING,
    /** Wiring, sockets or lighting. */
    ELECTRICAL,
    /** A fitted or supplied appliance. */
    APPLIANCE,
    /** Heating, ventilation or air conditioning. */
    HVAC,
    /** The fabric of the building. */
    STRUCTURAL,
    /** Infestation. */
    PEST,
    /** Alarms, extinguishers, locks and the like. */
    SAFETY_EQUIPMENT,
    /** A cleanliness problem needing more than a turnover. */
    CLEANLINESS,
    /** Connectivity. */
    NETWORK,
    /** Locks, keypads or entry systems. */
    ACCESS_HARDWARE,
    /** Anything else, described in the record. */
    OTHER
}

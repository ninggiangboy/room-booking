package dev.ngb.backend.discovery.internal.model.profile;

/**
 * The geographic level a prior was computed at.
 */
public enum PriorScopeLevel {

    /** Neighborhood. */
    NEIGHBORHOOD,

    /** Locality. */
    LOCALITY,

    /** Admin area. */
    ADMIN_AREA,

    /** Country. */
    COUNTRY,

    /** Global. */
    GLOBAL
}

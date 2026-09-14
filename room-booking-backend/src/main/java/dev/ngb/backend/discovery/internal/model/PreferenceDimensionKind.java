package dev.ngb.backend.discovery.internal.model;

/**
 * The kind of thing a preference or a session intent is about.
 */
public enum PreferenceDimensionKind {

    /** Aspect. */
    ASPECT,

    /** Price. */
    PRICE,

    /** Room type. */
    ROOM_TYPE,

    /** Amenity. */
    AMENITY,

    /** Location. */
    LOCATION,

    /** Policy. */
    POLICY,

    /** Trip shape. */
    TRIP_SHAPE,

    /** Host affinity. */
    HOST_AFFINITY
}

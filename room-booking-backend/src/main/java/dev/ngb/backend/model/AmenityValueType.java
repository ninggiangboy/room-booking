package dev.ngb.backend.model;

/**
 * What shape an amenity's value takes.
 *
 * <p>Most amenities are present or absent, but some carry a number a guest filters on — how many
 * beds, how many parking spaces — and modelling those as separate boolean keys makes them
 * unsearchable as quantities.</p>
 */
public enum AmenityValueType {
    /** Present or absent. */
    BOOLEAN,
    /** Present with a quantity. */
    COUNT,
    /** Present with free text. */
    TEXT,
    /** Present with one of a fixed set of values. */
    ENUM
}

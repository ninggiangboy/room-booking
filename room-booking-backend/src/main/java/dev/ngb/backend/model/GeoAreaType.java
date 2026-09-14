package dev.ngb.backend.model;

/**
 * What kind of place a destination row describes.
 *
 * <p>Deliberately generic. Countries do not agree on what sits between a country and a street, so a
 * province/district/ward hierarchy is expressed as {@code ADMIN_AREA} rows at differing
 * administrative levels rather than as separate types. A type that named Vietnamese administrative
 * tiers would have to be widened, and every stored row reinterpreted, the first time a second
 * country was added.</p>
 */
public enum GeoAreaType {

    /** A sovereign country; the root of a hierarchy, with no parent. */
    COUNTRY,

    /** An administrative subdivision, whose depth is carried by its administrative level. */
    ADMIN_AREA,

    /** A populated place a guest would name as their destination. */
    LOCALITY,

    /** A district within a locality, at the granularity a resident would recognise. */
    NEIGHBORHOOD,

    /** A landmark searchable as a destination in its own right. */
    POINT_OF_INTEREST
}

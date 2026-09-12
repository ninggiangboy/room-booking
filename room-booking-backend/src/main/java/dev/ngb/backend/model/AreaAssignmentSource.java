package dev.ngb.backend.model;

/**
 * How a property came to be assigned to a geographic area.
 *
 * <p>Recorded because assignments are derived and periodically recomputed. A {@link #BOUNDARY}
 * assignment can be rebuilt from the next catalog import; a {@link #HOST_DECLARED} or
 * {@link #OPERATOR} one must survive it. Without the source, a reimport silently discards every
 * human correction anyone has ever made.</p>
 */
public enum AreaAssignmentSource {
    /** Derived from the area's polygon containing the property's point. */
    BOUNDARY,
    /** Derived from distance, where the area has no usable boundary. */
    PROXIMITY,
    /** Stated by the host. */
    HOST_DECLARED,
    /** Corrected by a platform operator. */
    OPERATOR,
    /** Carried in from an external data import. */
    IMPORT
}

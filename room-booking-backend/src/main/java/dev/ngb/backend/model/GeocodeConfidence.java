package dev.ngb.backend.model;

/**
 * How reliable a property's resolved coordinates are.
 *
 * <p>Recorded because a low-confidence geocode must not be presented as an exact position: a guest
 * choosing a property by its distance to a beach is relying on the point being real.
 * {@link #HOST_PINNED} is tracked separately because a host dropping their own pin is a different
 * kind of evidence from a geocoder's guess, and is usually the better one.</p>
 */
public enum GeocodeConfidence {
    /** Resolved to the exact address. */
    EXACT,
    /** Resolved to the street or building with high confidence. */
    HIGH,
    /** Resolved to a neighbourhood. */
    MEDIUM,
    /** Resolved only to a locality or worse. */
    LOW,
    /** Positioned by the host rather than a geocoder. */
    HOST_PINNED
}

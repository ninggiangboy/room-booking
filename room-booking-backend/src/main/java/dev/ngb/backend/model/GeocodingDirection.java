package dev.ngb.backend.model;

/**
 * Which way a geocoding request went.
 *
 * <p>{@link #FORWARD} turns an address into coordinates, which is what happens when a host types
 * their address. {@link #REVERSE} turns coordinates into an address, which is what happens when a
 * host drops a pin on a map instead.</p>
 */
public enum GeocodingDirection {
    /** Address in, coordinates out. */
    FORWARD,
    /** Coordinates in, address out. */
    REVERSE
}

package dev.ngb.backend.model;

/**
 * How a travel time between two points was measured.
 *
 * <p>Always stored beside the time itself, because "18 minutes" means nothing without saying by
 * what: eighteen minutes on foot and eighteen minutes by car describe very different distances.</p>
 */
public enum TravelMode {
    /** On foot. */
    WALKING,
    /** By car. */
    DRIVING,
    /** By public transport. */
    TRANSIT,
    /** By bicycle. */
    CYCLING
}

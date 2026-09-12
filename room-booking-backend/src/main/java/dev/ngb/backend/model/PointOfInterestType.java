package dev.ngb.backend.model;

/**
 * Kind of landmark a guest measures distance to.
 *
 * <p>Distinct from a {@code geo_areas} row of type {@code POINT_OF_INTEREST}, which is a curated
 * destination a guest <em>searches for</em>. These are places that appear on a listing page as "1.2
 * km from Ben Thanh Market" — the two have different curation standards, and conflating them would
 * make every measured landmark also a searchable destination.</p>
 */
public enum PointOfInterestType {
    /** An airport. */
    AIRPORT,
    /** A mainline railway station. */
    TRAIN_STATION,
    /** A long-distance bus terminal. */
    BUS_STATION,
    /** An urban metro or subway station. */
    METRO_STATION,
    /** A ferry terminal or sea port. */
    PORT,
    /** A beach. */
    BEACH,
    /** A notable landmark. */
    LANDMARK,
    /** A museum or gallery. */
    MUSEUM,
    /** A park or green space. */
    PARK,
    /** A shopping centre or market. */
    SHOPPING,
    /** A hospital or clinic. */
    HOSPITAL,
    /** A university or college. */
    UNIVERSITY,
    /** A conference or exhibition venue. */
    CONVENTION_CENTRE,
    /** Anything not covered above. */
    OTHER
}

package dev.ngb.backend.model;

/**
 * What kind of physical place a property is.
 *
 * <p>Distinct from {@link RoomType}: a property type describes the building, a room type describes
 * what a guest gets inside it. A hotel can sell private rooms and a house can be let whole, and
 * conflating the two makes both unsayable.</p>
 */
public enum PropertyType {
    /** A self-contained flat within a larger building. */
    APARTMENT,
    /** A standalone house. */
    HOUSE,
    /** A detached property, usually with private grounds. */
    VILLA,
    /** A managed hotel with reception service. */
    HOTEL,
    /** Budget accommodation, typically with shared rooms. */
    HOSTEL,
    /** A small owner-run lodging. */
    GUESTHOUSE,
    /** A hotel with substantial on-site facilities. */
    RESORT,
    /** Accommodation in the host's own home. */
    HOMESTAY,
    /** A small independent design-led hotel. */
    BOUTIQUE_HOTEL,
    /** An apartment let with hotel-style services. */
    SERVICED_APARTMENT,
    /** Anything not covered above. */
    OTHER
}

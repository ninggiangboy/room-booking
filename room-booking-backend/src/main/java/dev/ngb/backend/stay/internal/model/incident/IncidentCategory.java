package dev.ngb.backend.stay.internal.model.incident;

/**
 * What kind of thing went wrong.
 *
 * <p>Category and severity are separate: a cleanliness report can be urgent, and a safety report
 * can be informational if nobody declared danger.</p>
 */
public enum IncidentCategory {
    /** The guest cannot get in. */
    CANNOT_ACCESS,
    /** Nobody is answering. */
    HOST_UNREACHABLE,
    /** The property was not prepared. */
    PROPERTY_NOT_READY,
    /** The property materially differs from the listing. */
    LISTING_MISMATCH,
    /** Power, water, heating or similar has failed. */
    UTILITY_FAILURE,
    /** Something is broken. */
    MAINTENANCE,
    /** A promised amenity is unavailable. */
    AMENITY_FAILURE,
    /** The property is not clean. */
    CLEANLINESS,
    /** Noise is affecting the stay. */
    NOISE,
    /** Something was left behind or is missing. */
    LOST_PROPERTY,
    /** A concern about who can get in. */
    ACCESS_SECURITY,
    /** A report of danger or harm. */
    SAFETY_REPORT
}

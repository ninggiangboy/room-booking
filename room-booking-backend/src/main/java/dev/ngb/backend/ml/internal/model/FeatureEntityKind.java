package dev.ngb.backend.ml.internal.model;

/**
 * What a feature, label or prediction is keyed by.
 *
 * <p>A set keyed by one of these cannot contain a member keyed by another: there is no join that
 * makes the lookup well defined, and whatever the serving path returned for it would be an
 * accident.</p>
 */
public enum FeatureEntityKind {

    /** The person searching or booking. */
    GUEST,

    /** The person offering supply. */
    HOST,

    /** One public offering. */
    LISTING,

    /** One physical property. */
    PROPERTY,

    /** One sellable room or unit type. */
    ACCOMMODATION_TYPE,

    /** One reservation. */
    BOOKING,

    /** One browsing session. */
    SESSION,

    /** One search request. */
    QUERY,

    /** One market. */
    MARKET,

    /** One place guests search for. */
    DESTINATION
}

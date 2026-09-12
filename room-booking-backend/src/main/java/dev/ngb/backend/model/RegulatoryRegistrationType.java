package dev.ngb.backend.model;

/**
 * Kind of local authorisation a host holds for letting accommodation.
 *
 * <p>Short-term rental is regulated locally, so these are properties of a jurisdiction rather than of
 * the platform. A registration may also carry an annual night limit the calendar has to enforce.</p>
 */
public enum RegulatoryRegistrationType {
    /** Permission to let the property short-term. */
    RENTAL_PERMIT,
    /** Registration with a tourism authority. */
    TOURISM_REGISTRATION,
    /** Confirmation that letting is permitted under local zoning. */
    ZONING_CLEARANCE,
    /** Fire-safety certification. */
    FIRE_SAFETY,
    /** A general licence to trade. */
    BUSINESS_LICENCE
}

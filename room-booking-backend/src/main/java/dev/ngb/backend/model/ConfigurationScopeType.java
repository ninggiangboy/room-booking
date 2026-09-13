package dev.ngb.backend.model;

/**
 * The scope a configuration setting or feature flag state applies at. Resolution runs from the
 * global floor upwards, and the schema says which of these it makes sense at.
 */
public enum ConfigurationScopeType {

    /** The floor every narrower setting overrides; always the lowest priority. */
    GLOBAL,

    /** Applies within one market. */
    MARKET,

    /** Applies within one organization. */
    ORGANIZATION,

    /** Applies within one property. */
    PROPERTY,

    /** Applies to one listing. */
    LISTING,

    /** Applies to a named segment of traffic. */
    SEGMENT
}

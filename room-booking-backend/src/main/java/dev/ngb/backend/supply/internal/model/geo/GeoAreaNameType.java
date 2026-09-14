package dev.ngb.backend.supply.internal.model.geo;

/**
 * How a stored name relates to what a destination is actually called.
 *
 * <p>The distinction is what lets one row be matched against and a different row be displayed. A
 * guest may search a destination by a colloquial or obsolete name and still expect to be shown the
 * current official one; collapsing these into a single list of names would make the two
 * indistinguishable.</p>
 */
public enum GeoAreaNameType {

    /** The name to display in this language; at most one per destination and language. */
    PREFERRED,

    /** An abbreviated form suitable where space is limited. */
    SHORT,

    /** A colloquial or alternative name worth matching a search against. */
    ALIAS,

    /** A former name, kept so an older query still finds the place it named. */
    HISTORIC
}

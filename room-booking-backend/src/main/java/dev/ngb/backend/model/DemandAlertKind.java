package dev.ngb.backend.model;

/**
 * What a guest asked to be told about.
 */
public enum DemandAlertKind {

    /** The price fell below an observed baseline. */
    PRICE_DROP,

    /** Something became bookable. */
    AVAILABILITY_OPENED,

    /** Fewer than a named number of units remain. */
    LAST_UNITS,

    /** A saved search found something new. */
    SAVED_SEARCH_MATCH
}

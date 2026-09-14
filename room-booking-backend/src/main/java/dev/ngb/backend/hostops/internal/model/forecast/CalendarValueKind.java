package dev.ngb.backend.hostops.internal.model.forecast;

/**
 * The calendar values a host can see the source of.
 */
public enum CalendarValueKind {

    /** Nightly price. */
    NIGHTLY_PRICE,

    /** Minimum stay. */
    MINIMUM_STAY,

    /** Maximum stay. */
    MAXIMUM_STAY,

    /** Closed to arrival. */
    CLOSED_TO_ARRIVAL,

    /** Closed to departure. */
    CLOSED_TO_DEPARTURE,

    /** Closed to stay. */
    CLOSED_TO_STAY,

    /** Sellable quantity. */
    SELLABLE_QUANTITY
}

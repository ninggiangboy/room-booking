package dev.ngb.backend.model;

/**
 * What a demand forecast run reasons from.
 */
public enum DemandForecastBasis {

    /** Search demand. */
    SEARCH_DEMAND,

    /** Booking pace. */
    BOOKING_PACE,

    /** Blended. */
    BLENDED,

    /** Seasonal naive. */
    SEASONAL_NAIVE
}

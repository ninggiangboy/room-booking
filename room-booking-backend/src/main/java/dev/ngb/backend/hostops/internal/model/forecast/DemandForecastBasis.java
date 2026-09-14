package dev.ngb.backend.hostops.internal.model.forecast;

import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;

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

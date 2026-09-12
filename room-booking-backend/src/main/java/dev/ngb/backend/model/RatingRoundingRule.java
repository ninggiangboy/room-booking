package dev.ngb.backend.model;

/**
 * How a computed average is rounded for display.
 */
public enum RatingRoundingRule {
    /** One decimal place, halves away from zero. */
    HALF_UP_ONE_DECIMAL,
    /** One decimal place, halves to even. */
    HALF_EVEN_ONE_DECIMAL,
    /** One decimal place, always downwards. */
    FLOOR_ONE_DECIMAL
}

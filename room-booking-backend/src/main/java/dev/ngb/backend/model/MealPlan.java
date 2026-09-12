package dev.ngb.backend.model;

/**
 * What food is included in a rate plan.
 *
 * <p>Part of the offer rather than the room, which is why it belongs to the rate plan: the same room
 * is commonly sold twice at different prices, once with breakfast and once without.</p>
 */
public enum MealPlan {
    /** No meals included. */
    ROOM_ONLY,
    /** Breakfast included. */
    BREAKFAST,
    /** Breakfast and one other meal included. */
    HALF_BOARD,
    /** All meals included. */
    FULL_BOARD,
    /** Meals and drinks included. */
    ALL_INCLUSIVE
}

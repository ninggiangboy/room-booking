package dev.ngb.backend.model;

/**
 * What a price rule reacts to.
 *
 * <p>The type is the rule's contract with the engine: it determines which facts the condition may
 * read and in what order compatible rules compose. Two rules of the same type are usually rivals for
 * the same night and are resolved by priority.</p>
 */
public enum PriceRuleType {
    /** Discounts longer stays, typically weekly or monthly. */
    LENGTH_OF_STAY_DISCOUNT,
    /** Rewards booking well ahead of the stay. */
    EARLY_BIRD,
    /** Discounts nights close to arrival that would otherwise go empty. */
    LAST_MINUTE,
    /** Prices a night stranded between two bookings, too short to sell normally. */
    ORPHAN_NIGHT,
    /** Varies price by day of week. */
    DAY_OF_WEEK,
    /** Varies price across a named season or event window. */
    SEASONAL,
    /** Moves price in response to observed or forecast occupancy. */
    OCCUPANCY_RESPONSE,
    /** Targets specific remaining gaps in the calendar. */
    GAP_FILL,
    /** Refuses to let other rules push the price below a margin floor. */
    MINIMUM_MARGIN_GUARD,
    /** Adds an amount for a qualifying condition rather than removing one. */
    SURCHARGE
}

package dev.ngb.backend.hostops.internal.model.forecast;

/**
 * The shapes of promotion the platform suggests to hosts.
 */
public enum PromotionSuggestionKind {

    /** Early bird. */
    EARLY_BIRD,

    /** Last minute. */
    LAST_MINUTE,

    /** Length of stay. */
    LENGTH_OF_STAY,

    /** Weekly. */
    WEEKLY,

    /** Monthly. */
    MONTHLY,

    /** Orphan night. */
    ORPHAN_NIGHT,

    /** Seasonal. */
    SEASONAL,

    /** New listing. */
    NEW_LISTING
}

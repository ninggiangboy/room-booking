package dev.ngb.backend.model;

/**
 * The scope a remedy line's ceiling is measured over, or that it has none.
 */
public enum RemedyLineCeilingScope {

    /** Per case. */
    PER_CASE,

    /** Per booking. */
    PER_BOOKING,

    /** Per source line. */
    PER_SOURCE_LINE,

    /** Per claim item. */
    PER_CLAIM_ITEM,

    /** Per program. */
    PER_PROGRAM,

    /** Per user period. */
    PER_USER_PERIOD,

    /** Not applicable. */
    NOT_APPLICABLE
}

package dev.ngb.backend.model;

/**
 * The scope a monetary ceiling is measured over.
 */
public enum CeilingScope {

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
    PER_USER_PERIOD
}

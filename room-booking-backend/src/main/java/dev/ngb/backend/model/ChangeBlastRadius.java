package dev.ngb.backend.model;

/**
 * How far a change reaches: one scope, one market, or everybody.
 */
public enum ChangeBlastRadius {

    /** Reaches one organization, property or listing. */
    SCOPED,

    /** Reaches one market. */
    MARKET,

    /** Reaches everybody. */
    GLOBAL
}

package dev.ngb.backend.admin.internal.model;

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

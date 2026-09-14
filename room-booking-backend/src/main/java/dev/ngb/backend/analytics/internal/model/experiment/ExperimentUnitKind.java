package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * What is being randomised, or analysed.
 *
 * <p>The choice follows interference risk: a guest for persistent experiences, a listing or host
 * for supply-side controls, a market or time switchback where spillover is strong.</p>
 */
public enum ExperimentUnitKind {

    /** A guest, for persistent experiences. */
    USER,

    /** One declared session, where carryover is acceptable. */
    SESSION,

    /** A listing, for supply-side controls. */
    LISTING,

    /** A host, where the treatment is about their tools or economics. */
    HOST,

    /** One booking, for post-contract communication. */
    BOOKING,

    /** A whole market, where spillover between units is strong. */
    MARKET,

    /** Alternating time slices in one market. */
    TIME_SWITCHBACK
}

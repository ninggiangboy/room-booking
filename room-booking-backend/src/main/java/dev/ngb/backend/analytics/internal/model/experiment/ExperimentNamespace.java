package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * The surface an experiment competes for.
 *
 * <p>A closed list, because collision detection depends on it. A team that could invent its own
 * namespace string would have opted out of every exclusion rule.</p>
 */
public enum ExperimentNamespace {

    /** The order of search results. */
    SEARCH_RANK,

    /** The price a guest is shown; never randomised by guest. */
    PUBLIC_PRICE,

    /** The booking and payment flow. */
    CHECKOUT,

    /** Conversations between guest and host. */
    MESSAGING,

    /** Outbound notifications. */
    NOTIFICATION,

    /** The tools a host sets prices with; never randomised by guest. */
    HOST_PRICING_TOOLS,

    /** How reviews and reputation are shown. */
    REVIEW_PRESENTATION,

    /** Account and listing onboarding. */
    ONBOARDING,

    /** Support and case handling. */
    SUPPORT
}

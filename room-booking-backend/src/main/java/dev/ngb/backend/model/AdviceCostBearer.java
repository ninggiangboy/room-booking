package dev.ngb.backend.model;

/**
 * Who bears the cost of following a piece of advice. Stating it is the fourth of the four facts a
 * recommendation owes the host.
 */
public enum AdviceCostBearer {

    /** The host funds it. */
    HOST,

    /** The platform funds it. */
    PLATFORM,

    /** Both fund it, in a split recorded on the advice itself. */
    SHARED,

    /** Following it costs the host nothing but their time. */
    NO_DIRECT_COST
}

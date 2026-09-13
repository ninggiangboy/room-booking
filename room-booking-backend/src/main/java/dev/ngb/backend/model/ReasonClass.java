package dev.ngb.backend.model;

/**
 * What kind of claim a recommendation reason makes.
 *
 * <p>{@code SPONSORED} is paid placement, always labelled and never personalized.</p>
 */
public enum ReasonClass {

    /** A claim about how well the listing is rated. */
    QUALITY,

    /** A claim about what the trip total buys compared with similar stays. */
    VALUE,

    /** A claim about where the listing is. */
    LOCATION,

    /** A claim that a specific aspect matches what the guest cares about. */
    ASPECT_MATCH,

    /** A claim that the price sits where this guest usually chooses. */
    PRICE_FIT,

    /** A claim about the host delivering what was promised. */
    RELIABILITY,

    /** A claim about the dates being open. */
    AVAILABILITY,

    /** A statement that the listing is new and has little evidence yet. */
    NEW_LISTING,

    /** A paid-placement disclosure, never an explanation of relevance. */
    SPONSORED
}

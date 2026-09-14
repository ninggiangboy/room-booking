package dev.ngb.backend.model;

/**
 * What a growth campaign is trying to change.
 */
public enum CampaignObjective {

    /** Bring in people who have never booked. */
    ACQUISITION,

    /** Bring back people who have stopped booking. */
    REACTIVATION,

    /** Move demand toward a place. */
    DESTINATION,

    /** Bring in or activate hosts. */
    SUPPLY_GROWTH,

    /** Keep people who already book. */
    RETENTION,

    /** Recover people who left after a bad experience. */
    WINBACK
}

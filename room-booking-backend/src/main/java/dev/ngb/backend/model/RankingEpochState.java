package dev.ngb.backend.model;

/**
 * Where a ranking epoch stands.
 *
 * <p>An epoch pulled out from under live cursors is invalidated and owes a reason; one that simply
 * reached its end is closed.</p>
 */
public enum RankingEpochState {

    /** Serving, and cursors bound to it remain valid. */
    OPEN,

    /** Reached its end normally. */
    CLOSED,

    /** Pulled out from under live cursors, and owes a reason. */
    INVALIDATED
}

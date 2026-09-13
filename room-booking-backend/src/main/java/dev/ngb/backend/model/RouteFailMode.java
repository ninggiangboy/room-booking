package dev.ngb.backend.model;

/**
 * Whether a consumer proceeds without a prediction or refuses to proceed.
 *
 * <p>This belongs to the route rather than the model: the same model can be safe to skip on a
 * search page and unsafe to skip at a payout decision.</p>
 */
public enum RouteFailMode {

    /** Continue the consumer's flow without the model. */
    FAIL_OPEN,

    /** Stop the consumer's flow rather than act without the model. */
    FAIL_CLOSED
}

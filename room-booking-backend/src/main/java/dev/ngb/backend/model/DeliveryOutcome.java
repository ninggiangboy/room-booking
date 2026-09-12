package dev.ngb.backend.model;

/**
 * What one provider observation says about an attempt.
 *
 * <p>The attempt state is the reduction of every observation; this is one observation's reading
 * of it, kept so the reduction can be re-derived later.</p>
 */
public enum DeliveryOutcome {
    /** The provider acknowledged receipt. */
    ACCEPTED,
    /** It reached the destination. */
    DELIVERED,
    /** The destination rejected it. */
    BOUNCED,
    /** The recipient reported it as unwanted. */
    COMPLAINED,
    /** The provider reported a failure. */
    FAILED,
    /** The event does not settle the outcome. */
    UNKNOWN
}

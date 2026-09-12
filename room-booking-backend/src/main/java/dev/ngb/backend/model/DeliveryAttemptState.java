package dev.ngb.backend.model;

/**
 * How far one crossing to one provider has got.
 *
 * <p>{@code ACCEPTED} means provider receipt only. Reduction is monotonic by evidence precedence:
 * a late bounce or complaint may follow a delivered event without erasing it.</p>
 */
public enum DeliveryAttemptState {
    /** Created, not yet claimed. */
    PLANNED,
    /** Held by a worker under a lease. */
    CLAIMED,
    /** Crossing to the provider. */
    SUBMITTING,
    /** The provider took it. */
    ACCEPTED,
    /** The provider reported delivery. */
    DELIVERED,
    /** The destination rejected it. */
    BOUNCED,
    /** The recipient reported it as unwanted. */
    COMPLAINED,
    /** Submission failed; the category says how. */
    FAILED,
    /** Submitted with no proven outcome; query or reconcile before sending again. */
    UNKNOWN
}

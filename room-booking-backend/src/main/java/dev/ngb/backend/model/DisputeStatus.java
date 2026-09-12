package dev.ngb.backend.model;

/**
 * Progress of one provider dispute case.
 *
 * <p>The terminal values are the outcome; there is deliberately no separate outcome column for the
 * two to disagree about.</p>
 */
public enum DisputeStatus {
    /** Opened, with no response yet required. */
    INQUIRY_OR_RETRIEVAL,
    /** A response is required, by a deadline. */
    ACTION_REQUIRED,
    /** Representment sent and frozen. */
    EVIDENCE_SUBMITTED,
    /** The provider or scheme is deciding. */
    UNDER_REVIEW,
    /** Decided in the platform's favour. */
    WON,
    /** Decided against the platform. */
    LOST,
    /** Conceded without representment. */
    ACCEPTED,
    /** Closed because the response deadline passed. */
    EXPIRED
}

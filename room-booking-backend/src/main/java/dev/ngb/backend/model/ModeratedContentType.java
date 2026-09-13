package dev.ngb.backend.model;

/**
 * What kind of content is under moderation.
 */
public enum ModeratedContentType {
    /** Listing description, title or house rules. */
    LISTING_TEXT,
    /** A listing photograph or video. */
    LISTING_MEDIA,
    /** A message between parties. */
    MESSAGE,
    /** A file attached to a message. */
    MESSAGE_ATTACHMENT,
    /** The text of a review. */
    REVIEW_TEXT,
    /** A host response to a review. */
    REVIEW_RESPONSE,
    /** Media attached to a review. */
    REVIEW_MEDIA,
    /** A profile field. */
    PROFILE_FIELD,
    /** A profile photograph. */
    PROFILE_MEDIA,
    /** A note on a support case. */
    SUPPORT_NOTE,
    /** Something reported during a stay. */
    INCIDENT_REPORT;
}

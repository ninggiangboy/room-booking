package dev.ngb.backend.model;

/**
 * What the author has done with their review.
 */
public enum ReviewAuthoringState {
    /** Private to the author; not moderation or publication truth. */
    DRAFT,
    /** A revision has been selected as the submission. */
    SUBMITTED_FINAL,
    /** The author took it back. */
    AUTHOR_WITHDRAWN
}

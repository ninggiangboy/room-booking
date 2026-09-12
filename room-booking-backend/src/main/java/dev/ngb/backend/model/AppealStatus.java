package dev.ngb.backend.model;

/**
 * Where a verification appeal currently sits.
 *
 * <p>A {@link #CLOSED} appeal must name its reviewer and its outcome; the database refuses one that
 * does not, because an appeal that closes with no reviewer and no conclusion is a refusal with extra
 * steps.</p>
 */
public enum AppealStatus {
    /** Submitted and awaiting a reviewer. */
    OPEN,
    /** Being reviewed. */
    IN_REVIEW,
    /** Reviewed and concluded. */
    CLOSED,
    /** Withdrawn by the host before a conclusion. */
    WITHDRAWN
}

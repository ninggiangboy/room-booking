package dev.ngb.backend.review.internal.model.aggregate;

/**
 * Whether a reviewer attention profile may be read.
 */
public enum AttentionProfileStatus {
    /** The profile consumers should read. */
    CURRENT,
    /** Replaced by a newer computation. */
    SUPERSEDED,
    /** Being recomputed. */
    REBUILDING,
    /** The computation did not finish. */
    FAILED,
    /** The person opted out or asked for deletion. */
    WITHDRAWN
}

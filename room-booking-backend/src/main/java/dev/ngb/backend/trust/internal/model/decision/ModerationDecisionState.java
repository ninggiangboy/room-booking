package dev.ngb.backend.trust.internal.model.decision;

/**
 * Whether a moderation decision still stands.
 *
 * <p>One effective decision per revision, enforced by a partial unique index. Restoring or removing
 * later is a new decision naming the one it supersedes.</p>
 */
public enum ModerationDecisionState {
    /** The decision the owning domain should obey. */
    EFFECTIVE,
    /** Its window closed. */
    EXPIRED,
    /** A later decision replaced it. */
    SUPERSEDED
}

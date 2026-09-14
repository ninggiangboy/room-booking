package dev.ngb.backend.support.internal.model.decision;

/**
 * Where a decision stands.
 *
 * <p>An effective decision is immutable; a correction is a new decision naming the one it supersedes.</p>
 */
public enum CaseDecisionState {

    /** Draft. */
    DRAFT,

    /** Pending approval. */
    PENDING_APPROVAL,

    /** Effective. */
    EFFECTIVE,

    /** Superseded. */
    SUPERSEDED,

    /** Voided. */
    VOIDED
}

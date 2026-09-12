package dev.ngb.backend.model;

/**
 * How far a versioned arrival instruction has progressed.
 *
 * <p>Released versions are frozen by trigger: a change is a new version, never an edit.</p>
 */
public enum InstructionSetStatus {
    /** Being written; not retrievable by anybody. */
    DRAFT,
    /** Approved but not yet in force. */
    APPROVED,
    /** In force and evaluated on every retrieval. */
    RELEASED,
    /** A later version replaced it; it is kept for audit. */
    SUPERSEDED,
    /** Withdrawn from future retrieval, with a reason. */
    REVOKED
}

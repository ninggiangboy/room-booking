package dev.ngb.backend.support.internal.model.case_;

/**
 * The relation type of {@code case_relationships}.
 */
public enum CaseRelationType {

    /** Duplicate of. */
    DUPLICATE_OF,

    /** Related to. */
    RELATED_TO,

    /** Child of. */
    CHILD_OF,

    /** Merged into. */
    MERGED_INTO,

    /** Unmerged from. */
    UNMERGED_FROM,

    /** Caused by. */
    CAUSED_BY,

    /** Supersedes. */
    SUPERSEDES,

    /** Escalated from. */
    ESCALATED_FROM,

    /** References incident. */
    REFERENCES_INCIDENT,

    /** References claim. */
    REFERENCES_CLAIM,

    /** References provider dispute. */
    REFERENCES_PROVIDER_DISPUTE,

    /** References risk case. */
    REFERENCES_RISK_CASE
}

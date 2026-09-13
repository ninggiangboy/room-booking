package dev.ngb.backend.model;

/**
 * The visibility scope of {@code case_evidence_items}.
 */
public enum EvidenceVisibility {

    /** Submitter only. */
    SUBMITTER_ONLY,

    /** Internal only. */
    INTERNAL_ONLY,

    /** Participant shared. */
    PARTICIPANT_SHARED,

    /** Provider disclosed. */
    PROVIDER_DISCLOSED,

    /** Restricted authority. */
    RESTRICTED_AUTHORITY
}
